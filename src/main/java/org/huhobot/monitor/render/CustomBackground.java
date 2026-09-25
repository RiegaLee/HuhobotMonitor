package org.huhobot.monitor.render;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Loads a bounded local PNG and prepares it as a fixed-size dashboard wallpaper. */
public final class CustomBackground {
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_PIXELS = 32L * 1024L * 1024L;

    private CustomBackground() {
    }

    public static BufferedImage load(Path path, String fit, int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Target dimensions must be positive");
        String normalizedFit = Objects.requireNonNull(fit, "fit").trim().toLowerCase(Locale.ROOT);
        if (!"cover".equals(normalizedFit) && !"stretch".equals(normalizedFit)) {
            throw new IllegalArgumentException("render.custom-background.fit must be cover or stretch");
        }

        Path normalized = Objects.requireNonNull(path, "custom background").toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Missing custom background: " + normalized);
        }
        try {
            if (Files.size(normalized) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("custom background exceeds 16 MiB: " + normalized);
            }
            BufferedImage source = ImageIO.read(normalized.toFile());
            if (source == null) throw new IllegalArgumentException("Unreadable custom background: " + normalized);
            long pixels = (long) source.getWidth() * (long) source.getHeight();
            if (source.getWidth() < 1 || source.getHeight() < 1 || pixels > MAX_PIXELS) {
                throw new IllegalArgumentException("custom background has unsafe dimensions: " + normalized);
            }
            return resize(source, normalizedFit, width, height);
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read custom background: " + normalized, error);
        }
    }

    static BufferedImage resize(BufferedImage source, String fit, int width, int height) {
        if (!"cover".equals(fit) && !"stretch".equals(fit)) {
            throw new IllegalArgumentException("fit must be cover or stretch");
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if ("stretch".equals(fit)) {
                graphics.drawImage(source, 0, 0, width, height, null);
            } else {
                double scale = Math.max(
                    (double) width / (double) source.getWidth(),
                    (double) height / (double) source.getHeight()
                );
                int scaledWidth = Math.max(1, (int) Math.ceil(source.getWidth() * scale));
                int scaledHeight = Math.max(1, (int) Math.ceil(source.getHeight() * scale));
                int x = (width - scaledWidth) / 2;
                int y = (height - scaledHeight) / 2;
                graphics.drawImage(source, x, y, scaledWidth, scaledHeight, null);
            }
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
