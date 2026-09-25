package org.huhobot.monitor.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplyConfigTest {
    @Test void oldConfigDefaultsToImagesWithFallback() {
        ReplyConfig config = ReplyConfig.load(new YamlConfiguration());
        assertFalse(config.isTextMode());
        assertTrue(config.isFallbackToText());
    }

    @Test void acceptsTextAndDisabledFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reply.mode", " TEXT ");
        yaml.set("reply.fallback-to-text", false);
        ReplyConfig config = ReplyConfig.load(yaml);
        assertTrue(config.isTextMode());
        assertFalse(config.isFallbackToText());
    }

    @Test void rejectsUnknownModeRatherThanUnexpectedlyUploadingImages() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reply.mode", "txt");
        assertThrows(IllegalArgumentException.class, () -> ReplyConfig.load(yaml));
    }
}
