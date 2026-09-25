package org.huhobot.monitor.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomBackgroundConfigTest {
    @Test
    void loadsSafeLocalPngSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("render.custom-background.enabled", true);
        yaml.set("render.custom-background.file", "my-wallpaper.png");
        yaml.set("render.custom-background.fit", "stretch");

        CustomBackgroundConfig config = CustomBackgroundConfig.load(yaml);
        Path root = Paths.get("build", "custom-backgrounds").toAbsolutePath().normalize();
        assertTrue(config.isEnabled());
        assertEquals("stretch", config.getFit());
        assertEquals(root.resolve("my-wallpaper.png"), config.resolve(root));
    }

    @Test
    void rejectsTraversalAndUnknownFitMode() {
        YamlConfiguration traversal = new YamlConfiguration();
        traversal.set("render.custom-background.file", "../outside.png");
        assertThrows(IllegalArgumentException.class, () -> CustomBackgroundConfig.load(traversal));

        YamlConfiguration fit = new YamlConfiguration();
        fit.set("render.custom-background.fit", "contain");
        assertThrows(IllegalArgumentException.class, () -> CustomBackgroundConfig.load(fit));
    }
}
