package cn.huohuas001.huhobot.performance;

import cn.huohuas001.huhobot.performance.api.ImageReplyApi;
import cn.huohuas001.huhobot.performance.api.ImageReplyRequest;
import cn.huohuas001.huhobot.performance.api.ImageReplyResult;
import cn.huohuas001.huhobot.performance.bridge.BotCommandContext;
import cn.huohuas001.huhobot.performance.bridge.BuiltInCommandBridge;
import cn.huohuas001.huhobot.performance.bridge.HuHoBotBridge;
import cn.huohuas001.huhobot.performance.image.ReflectiveImageReplyApi;
import cn.huohuas001.huhobot.performance.model.PerformanceSnapshot;
import cn.huohuas001.huhobot.performance.model.PerformanceThresholds;
import cn.huohuas001.huhobot.performance.monitor.PerformanceMonitor;
import cn.huohuas001.huhobot.performance.render.PerformanceRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class PerformancePlugin extends JavaPlugin implements CommandExecutor {
    private final Set<String> inFlightGroups = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> lastRequestAt = new ConcurrentHashMap<String, Long>();

    private ExecutorService renderExecutor;
    private PerformanceMonitor monitor;
    private PerformanceRenderer renderer;
    private ImageReplyApi imageReplyApi;
    private BuiltInCommandBridge builtInBridge;
    private HuHoBotBridge eventBridge;
    private BukkitTask registrationRetryTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("huhobotperformance") != null) {
            getCommand("huhobotperformance").setExecutor(this);
        }

        String commandKey = commandKey();
        if (commandKey.isEmpty()) {
            getLogger().severe("config.yml 的 bot-command 不能为空");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        renderExecutor = Executors.newSingleThreadExecutor(namedThreads("HuHoBotPerformance-Render"));
        try {
            renderer = new PerformanceRenderer(
                getConfig().getString("render.font-family", ""),
                thresholds()
            );
            monitor = new PerformanceMonitor(this);
            monitor.start();

            builtInBridge = new BuiltInCommandBridge(this, this::acceptBotCommand);
            Plugin huHoBot = builtInBridge.getHuHoBotPlugin();
            if (huHoBot == null || !huHoBot.isEnabled()) throw new IllegalStateException("未找到已启用的 HuHoBotPenguin");
            imageReplyApi = new ReflectiveImageReplyApi(
                huHoBot,
                getLogger(),
                getConfig().getInt("image-api.max-bytes", 20 * 1024 * 1024)
            );
            Bukkit.getServicesManager().register(ImageReplyApi.class, imageReplyApi, this, ServicePriority.Normal);
            connectCommand(commandKey);
        } catch (Throwable error) {
            getLogger().log(Level.SEVERE, "HuHoBot 性能监控初始化失败", error);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("性能监控已就绪：运行核心 UI，单服实时采样，图片字节回复");
    }

    @Override
    public void onDisable() {
        if (registrationRetryTask != null) registrationRetryTask.cancel();
        if (builtInBridge != null) builtInBridge.disconnect();
        if (eventBridge != null) eventBridge.disconnect();
        if (monitor != null) monitor.stop();
        Bukkit.getServicesManager().unregisterAll(this);
        if (renderExecutor != null) renderExecutor.shutdownNow();
        inFlightGroups.clear();
        lastRequestAt.clear();
    }

    private void connectCommand(String commandKey) throws ReflectiveOperationException {
        if ("性能监控".equals(commandKey)) {
            BuiltInCommandBridge.ConnectResult result = builtInBridge.tryConnect();
            if (result == BuiltInCommandBridge.ConnectResult.CONNECTED) {
                logConnected();
                return;
            }
            if (result == BuiltInCommandBridge.ConnectResult.NOT_READY) {
                registrationRetryTask = getServer().getScheduler().runTaskTimer(this, () -> {
                    BuiltInCommandBridge.ConnectResult retry = builtInBridge.tryConnect();
                    if (retry == BuiltInCommandBridge.ConnectResult.CONNECTED) {
                        logConnected();
                        registrationRetryTask.cancel();
                        registrationRetryTask = null;
                    } else if (retry == BuiltInCommandBridge.ConnectResult.UNSUPPORTED) {
                        registrationRetryTask.cancel();
                        registrationRetryTask = null;
                        try {
                            connectEventFallback(commandKey);
                        } catch (ReflectiveOperationException error) {
                            getLogger().log(Level.SEVERE, "HuHoBot 命令后备入口注册失败", error);
                            getServer().getPluginManager().disablePlugin(this);
                        }
                    }
                }, 20L, 20L);
                getLogger().info("等待 HuHoBot QQ 客户端启动，随后自动注册 /性能监控");
                return;
            }
        }
        connectEventFallback(commandKey);
    }

    private void logConnected() {
        getLogger().info(builtInBridge.isAddonApiUsed()
            ? "已通过 PenguinAgent AddonAPI 注册 /性能监控"
            : "已通过 HuHoBot 主分支原生命令入口注册 /性能监控");
    }

    private void connectEventFallback(String commandKey) throws ReflectiveOperationException {
        eventBridge = new HuHoBotBridge(
            this,
            commandKey,
            getConfig().getBoolean("push-command-menu", true),
            this::acceptBotCommand
        );
        eventBridge.connect();
        getLogger().warning("当前 HuHoBot 分支不支持原生命令注册，已启用 OnBotCommand 兼容入口");
    }

    private void acceptBotCommand(BotCommandContext context) {
        if (Bukkit.isPrimaryThread()) handleBotCommand(context);
        else getServer().getScheduler().runTask(this, () -> handleBotCommand(context));
    }

    private void handleBotCommand(BotCommandContext context) {
        if (!context.getCommandArguments().trim().isEmpty()) {
            context.replyText(message("messages.usage", "用法：/{command}").replace("{command}", commandKey()));
            return;
        }

        String group = context.getGroupOpenId();
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, getConfig().getLong("cooldown-seconds", 5L)) * 1000L;
        Long previous = lastRequestAt.put(group, now);
        if ((previous != null && now - previous < cooldownMillis) || !inFlightGroups.add(group)) {
            context.replyText(message("messages.busy", "性能图正在生成，请稍候再试。"));
            return;
        }

        PerformanceSnapshot snapshot = monitor.capture(serverName());
        renderExecutor.execute(() -> {
            try {
                byte[] png = renderer.render(snapshot);
                ImageReplyResult result = imageReplyApi.reply(new ImageReplyRequest(
                    context.getSourceEvent(),
                    context.getGroupOpenId(),
                    context.getMessageId(),
                    context.getMessageSequence(),
                    " ",
                    "server-performance.png",
                    png
                ));
                if (!result.isSuccess()) {
                    getLogger().log(Level.WARNING, "性能监控图片发送失败，transport=" + result.getTransport()
                        + "：" + result.getMessage(), result.getCause());
                    context.replyText(message("messages.failed", "性能图生成或发送失败，请稍后再试。"));
                }
            } catch (Throwable error) {
                getLogger().log(Level.WARNING, "性能监控图片渲染失败", error);
                context.replyText(message("messages.failed", "性能图生成或发送失败，请稍后再试。"));
            } finally {
                inFlightGroups.remove(group);
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "bridge".equalsIgnoreCase(args[0])) return true;
        if (args.length > 0 && ("preview".equalsIgnoreCase(args[0]) || "test".equalsIgnoreCase(args[0]))) {
            return generatePreview(sender, args);
        }
        sender.sendMessage("HuHoBotPerformance " + getDescription().getVersion() + " 已启用；QQ 命令 /"
            + commandKey() + "；本地预览：/" + label + " preview [healthy|warning|critical|unavailable]");
        return true;
    }

    private boolean generatePreview(CommandSender sender, String[] args) {
        if (!sender.hasPermission("huhobotperformance.admin")) {
            sender.sendMessage("你没有权限生成性能监控预览。");
            return true;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "healthy";
        PerformanceSnapshot snapshot = previewSnapshot(mode);
        if (snapshot == null) {
            sender.sendMessage("预览状态只能是 healthy、warning、critical 或 unavailable。");
            return true;
        }

        sender.sendMessage("正在生成 " + mode + " 状态的运行核心预览……");
        renderExecutor.execute(() -> {
            try {
                byte[] png = renderer.render(snapshot);
                Path output = getDataFolder().toPath().resolve("preview-" + mode + ".png");
                Files.createDirectories(output.getParent());
                Files.write(output, png);
                sendOnMain(sender, "性能监控预览已生成：" + output.toAbsolutePath());
            } catch (Throwable error) {
                getLogger().log(Level.WARNING, "生成性能监控预览失败", error);
                sendOnMain(sender, "性能监控预览生成失败，请查看控制台日志。");
            }
        });
        return true;
    }

    private PerformanceSnapshot previewSnapshot(String mode) {
        long gib = 1024L * 1024L * 1024L;
        if ("healthy".equals(mode)) {
            return new PerformanceSnapshot(serverName(), Instant.now(), 19.9, 12.6, 34, 18,
                (long) (4.2 * gib), 8L * gib, 17, 50);
        }
        if ("warning".equals(mode)) {
            return new PerformanceSnapshot(serverName(), Instant.now(), 19.0, 43.7, 78, 65,
                (long) (6.5 * gib), 8L * gib, 38, 50);
        }
        if ("critical".equals(mode)) {
            return new PerformanceSnapshot(serverName(), Instant.now(), 16.8, 59.4, 94, 89,
                (long) (7.5 * gib), 8L * gib, 49, 50);
        }
        if ("unavailable".equals(mode)) {
            return new PerformanceSnapshot(serverName(), Instant.now(), Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 0L, 0L, 0, 50);
        }
        return null;
    }

    private void sendOnMain(CommandSender sender, String text) {
        getServer().getScheduler().runTask(this, () -> sender.sendMessage(text));
    }

    private String commandKey() {
        String configured = getConfig().getString("bot-command", "性能监控");
        return configured == null ? "" : configured.trim();
    }

    private String serverName() {
        String configured = getConfig().getString("server-name", "MinecraftServer");
        return configured == null || configured.trim().isEmpty() ? "MinecraftServer" : configured.trim();
    }

    private PerformanceThresholds thresholds() {
        return new PerformanceThresholds(
            getConfig().getDouble("thresholds.tps.warning-below", 19.5),
            getConfig().getDouble("thresholds.tps.critical-below", 18.0),
            getConfig().getDouble("thresholds.mspt.warning-above", 40.0),
            getConfig().getDouble("thresholds.mspt.critical-above", 50.0),
            getConfig().getDouble("thresholds.cpu.warning-above", 75.0),
            getConfig().getDouble("thresholds.cpu.critical-above", 90.0),
            getConfig().getDouble("thresholds.memory.warning-percent", 75.0),
            getConfig().getDouble("thresholds.memory.critical-percent", 90.0)
        );
    }

    private String message(String path, String fallback) {
        String value = getConfig().getString(path, fallback);
        return value == null ? fallback : value;
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
