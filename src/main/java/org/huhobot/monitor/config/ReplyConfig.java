package org.huhobot.monitor.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Text mode never initializes or calls the image pipeline. */
public final class ReplyConfig {
    private final boolean textMode;
    private final boolean fallbackToText;

    private ReplyConfig(boolean textMode, boolean fallbackToText) {
        this.textMode = textMode;
        this.fallbackToText = fallbackToText;
    }

    public static ReplyConfig load(ConfigurationSection config) {
        String mode = config.getString("reply.mode", "image").trim().toLowerCase(Locale.ROOT);
        if (!"image".equals(mode) && !"text".equals(mode)) {
            throw new IllegalArgumentException("reply.mode must be image or text");
        }
        return new ReplyConfig("text".equals(mode), config.getBoolean("reply.fallback-to-text", true));
    }

    public boolean isTextMode() { return textMode; }
    public boolean isFallbackToText() { return fallbackToText; }
}
