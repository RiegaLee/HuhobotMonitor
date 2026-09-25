package org.huhobot.monitor;

import cn.huohuas001.bot.events.commands.Commands;
import org.huhobot.monitor.bridge.BotCommandContext;
import org.huhobot.monitor.bridge.BuiltInPerformanceCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AddonIdentityTest {
    @Test
    void pluginMetadataAndDefaultCommandUseNewIdentity() throws Exception {
        try (InputStream plugin = getClass().getResourceAsStream("/plugin.yml");
             InputStream config = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(plugin);
            assertNotNull(config);
            PluginDescriptionFile metadata = new PluginDescriptionFile(plugin);
            assertEquals("HuhobotMonitor", metadata.getName());
            assertEquals(HuhobotMonitorPlugin.class.getName(), metadata.getMain());
            assertEquals("org.huhobot.monitor.HuhobotMonitorPlugin", metadata.getMain());
            assertEquals(1, metadata.getAuthors().size());
            assertEquals("HuhobotMonitor Contributors", metadata.getAuthors().get(0));
            assertTrue(metadata.getCommands().containsKey("huhobotmonitor"));
            assertEquals(1, metadata.getCommands().size());
            // Inspect YAML directly: Bukkit's permission loader requires a live server logger.
            try (InputStream pluginYaml = getClass().getResourceAsStream("/plugin.yml")) {
                YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(pluginYaml, StandardCharsets.UTF_8));
                assertEquals("op", descriptor.getString("permissions.huhobotmonitor.admin.default"));
            }
            YamlConfiguration settings = YamlConfiguration.loadConfiguration(new InputStreamReader(config, StandardCharsets.UTF_8));
            assertEquals("服务器状态", settings.getString("bot-command"));
            Commands command = BuiltInPerformanceCommand.class.getMethod("performance", Object.class).getAnnotation(Commands.class);
            assertEquals(settings.getString("bot-command"), command.command());
            assertEquals(command.command(), BotCommandContext.DEFAULT_COMMAND);
        }
    }

    @Test
    void nativeCommandParsesRenamedCommandAndArguments() throws Exception {
        BotCommandContext context = BotCommandContext.fromRawEvent(new Event());
        assertEquals("服务器状态", context.getCommandKey());
        assertEquals("extra", context.getCommandArguments());
        assertEquals("group", context.getGroupOpenId());
        assertEquals("message", context.getMessageId());
    }

    public static final class Event {
        public final RawMessage rawMessage = new RawMessage();
        public final String groupOpenId = "group";
        public final int msgSeq = 1;
    }

    public static final class RawMessage {
        public final String content = "<@!bot> /服务器状态 extra";
        public final String id = "message";
    }
}
