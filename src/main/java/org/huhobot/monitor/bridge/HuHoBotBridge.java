package org.huhobot.monitor.bridge;

import org.huhobot.monitor.util.Reflect;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Event-based compatibility bridge used when a HuHoBot branch has no native command entry. */
public final class HuHoBotBridge {
    private static final String EVENT_CLASS = "cn.huohuas001.huhobotPenguin.spigot.events.OnBotCommand";

    private final JavaPlugin owner;
    private final String commandKey;
    private final boolean pushMenu;
    private final Consumer<BotCommandContext> handler;
    private Plugin huHoBot;
    private Listener listener;
    private boolean commandRegistered;

    public HuHoBotBridge(JavaPlugin owner, String commandKey, boolean pushMenu, Consumer<BotCommandContext> handler) {
        this.owner = owner;
        this.commandKey = commandKey;
        this.pushMenu = pushMenu;
        this.handler = handler;
    }

    public void connect() throws ReflectiveOperationException {
        PluginManager manager = owner.getServer().getPluginManager();
        huHoBot = locatePlugin(manager);
        if (huHoBot == null || !huHoBot.isEnabled()) throw new IllegalStateException("未找到已启用的 HuHoBotPenguin");

        Class<?> rawEvent = Class.forName(EVENT_CLASS, false, huHoBot.getClass().getClassLoader());
        if (!Event.class.isAssignableFrom(rawEvent)) throw new ClassNotFoundException(EVENT_CLASS);
        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClass = (Class<? extends Event>) rawEvent;
        listener = new Listener() { };
        EventExecutor executor = (ignored, event) -> onEvent(event);
        manager.registerEvent(eventClass, listener, EventPriority.NORMAL, executor, owner, false);

        Object registered;
        try {
            registered = Reflect.invoke(huHoBot, "registerBotCommand", commandKey, "huhobotmonitor bridge", 0, pushMenu);
        } catch (NoSuchMethodException error) {
            registered = Reflect.invoke(huHoBot, "registerBotCommand", commandKey, "huhobotmonitor bridge");
        }
        commandRegistered = !(registered instanceof Boolean) || (Boolean) registered;
        if (!commandRegistered) throw new IllegalStateException("HuHoBot 拒绝注册命令：" + commandKey);
    }

    public void disconnect() {
        if (huHoBot != null && commandRegistered) {
            try {
                Reflect.invoke(huHoBot, "unregisterBotCommand", commandKey);
            } catch (Throwable error) {
                owner.getLogger().warning("注销 HuHoBot 命令失败：" + error.getMessage());
            }
        }
        commandRegistered = false;
    }

    private void onEvent(Event event) {
        try {
            BotCommandContext context = BotCommandContext.from(event);
            if (!commandKey.equals(context.getCommandKey())) return;
            context.cancel();
            handler.accept(context);
        } catch (Throwable error) {
            owner.getLogger().severe("处理 HuHoBot 命令事件失败：" + error.getMessage());
        }
    }

    private static Plugin locatePlugin(PluginManager manager) {
        Plugin named = manager.getPlugin("HuHoBotPenguin");
        if (named != null) return named;
        for (Plugin plugin : manager.getPlugins()) {
            Method method = Reflect.findCompatibleMethod(plugin.getClass(), "registerBotCommand", "key", "command", 0, true);
            if (method != null) return plugin;
        }
        return null;
    }
}
