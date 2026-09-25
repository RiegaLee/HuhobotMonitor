package org.huhobot.monitor.bridge;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.bot.addon.Addon;
import cn.huohuas001.bot.events.commands.BaseCommand;
import org.huhobot.monitor.util.Reflect;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Consumer;

/** Registers this plugin through the Addon entry point provided by current HuHoBot mainline. */
public final class BuiltInCommandBridge {
    public enum ConnectResult {
        CONNECTED,
        NOT_READY,
        UNSUPPORTED
    }

    private final JavaPlugin owner;
    private final Consumer<BotCommandContext> handler;
    private final Plugin huHoBot;
    private BaseCommand commandInstance;

    public BuiltInCommandBridge(JavaPlugin owner, Consumer<BotCommandContext> handler) {
        this.owner = owner;
        this.handler = handler;
        this.huHoBot = locatePlugin(owner.getServer().getPluginManager());
    }

    public ConnectResult tryConnect() {
        if (commandInstance != null) return ConnectResult.CONNECTED;
        if (huHoBot == null || !huHoBot.isEnabled()) return ConnectResult.UNSUPPORTED;
        try {
            BaseCommand candidate = new BuiltInPerformanceCommand(this::onRawEvent);
            Addon metadata = new Addon(
                owner.getName(),
                owner.getDescription().getVersion(),
                owner.getDescription().getDescription() == null
                    ? "" : owner.getDescription().getDescription(),
                String.join(", ", owner.getDescription().getAuthors())
            );
            QClient.INSTANCE.registerCommand(metadata, candidate);
            commandInstance = candidate;
            return ConnectResult.CONNECTED;
        } catch (Throwable error) {
            if (isNotReady(error)) return ConnectResult.NOT_READY;
            owner.getLogger().warning("HuHoBot Addon 指令注册失败：" + concise(error));
            return ConnectResult.UNSUPPORTED;
        }
    }

    /**
     * Current upstream does not expose unregisterCommand yet. Remove only this owned command
     * from its internal dispatch list so plugin reloads do not retain stale handlers.
     */
    public void disconnect() {
        if (commandInstance == null) return;
        try {
            Object handlerObject = readKotlinField(QClient.class, "groupMessageHandler");
            Object commands = Reflect.read(handlerObject, "commands");
            if (commands instanceof List) ((List<?>) commands).remove(commandInstance);
            QClient.INSTANCE.syncGroupPanels();
        } catch (Throwable error) {
            owner.getLogger().warning("注销 HuHoBot Addon 指令失败：" + concise(error));
        } finally {
            commandInstance = null;
        }
    }

    public boolean isAddonApiUsed() {
        return true;
    }

    public Plugin getHuHoBotPlugin() {
        return huHoBot;
    }

    private void onRawEvent(Object event) {
        try {
            handler.accept(BotCommandContext.fromRawEvent(event));
        } catch (Throwable error) {
            owner.getLogger().warning("读取 HuHoBot 群消息失败：" + concise(error));
        }
    }

    private static Object readKotlinField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = Reflect.findField(type, name);
        if (field == null) throw new NoSuchFieldException(type.getName() + "." + name);
        field.setAccessible(true);
        return field.get(Modifier.isStatic(field.getModifiers()) ? null : Reflect.kotlinObject(type));
    }

    private static boolean isNotReady(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && (message.contains("not been launched") || message.contains("not initialized"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static Plugin locatePlugin(PluginManager manager) {
        Plugin named = manager.getPlugin("HuHoBotPenguin");
        if (named != null) return named;
        for (Plugin plugin : manager.getPlugins()) {
            if (plugin.getClass().getName().contains("HuHoBot")) return plugin;
            Method method = Reflect.findCompatibleMethod(plugin.getClass(), "registerBotCommand", "a", "b");
            if (method != null) return plugin;
        }
        return null;
    }

    private static String concise(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return cursor.getClass().getSimpleName() + (cursor.getMessage() == null ? "" : ": " + cursor.getMessage());
    }
}
