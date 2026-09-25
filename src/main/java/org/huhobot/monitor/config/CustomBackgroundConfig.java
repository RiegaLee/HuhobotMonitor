package org.huhobot.monitor.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Validated local-only wallpaper settings for the performance dashboard. */
public final class CustomBackgroundConfig {
    private final boolean enabled;
    private final String fileName;
    private final String fit;

    private CustomBackgroundConfig(boolean enabled, String fileName, String fit) {
        this.enabled = enabled;
        this.fileName = safePng(fileName);
        this.fit = Objects.requireNonNull(fit, "render.custom-background.fit")
            .trim().toLowerCase(Locale.ROOT);
        if (!"cover".equals(this.fit) && !"stretch".equals(this.fit)) {
            throw new IllegalArgumentException("render.custom-background.fit must be cover or stretch");
        }
    }

    public static CustomBackgroundConfig load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new CustomBackgroundConfig(
            config.getBoolean("render.custom-background.enabled", false),
            config.getString("render.custom-background.file", "performance.png"),
            config.getString("render.custom-background.fit", "cover")
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFit() {
        return fit;
    }

    public Path resolve(Path backgroundsDirectory) {
        Path root = Objects.requireNonNull(backgroundsDirectory, "backgroundsDirectory")
            .toAbsolutePath().normalize();
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Custom background escapes its directory: " + fileName);
        }
        return resolved;
    }

    private static String safePng(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._-]+\\.png")) {
            throw new IllegalArgumentException(
                "render.custom-background.file must be a PNG file name without directories"
            );
        }
        return normalized;
    }
}
