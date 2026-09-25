package org.huhobot.monitor.preview;

import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;
import org.huhobot.monitor.render.PerformanceRenderer;
import org.huhobot.monitor.render.PerformanceTextRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Generates a deterministic design preview without starting a Minecraft server. */
public final class PerformancePreview {
    private PerformancePreview() {
    }

    public static void main(String[] args) throws Exception {
        File output = new File(args.length == 0 ? "build/preview/performance-preview-v2.png" : args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create preview directory: " + parent);
        }

        long gib = 1024L * 1024L * 1024L;
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            "main",
            Instant.parse("2026-09-04T06:36:10Z"),
            19.9,
            12.6,
            34.0,
            18.0,
            (long) (4.2 * gib),
            8L * gib,
            17,
            50
        );

        Path customBackground = args.length > 1 ? Paths.get(args[1]) : null;
        String fit = args.length > 2 ? args[2] : "cover";
        int blurRadius = args.length > 3 ? Integer.parseInt(args[3]) : PerformanceRenderer.DEFAULT_GLASS_BLUR_RADIUS;
        boolean adaptiveDimming = args.length > 4 ? Boolean.parseBoolean(args[4]) : true;
        double maximumDimming = args.length > 5 ? Double.parseDouble(args[5]) : 0.38;
        PerformanceRenderer renderer = new PerformanceRenderer(
            "", PerformanceThresholds.defaults(), customBackground, fit, 0.12, blurRadius,
            adaptiveDimming, maximumDimming
        );
        BufferedImage image = renderer.renderImage(snapshot);
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("PNG writer is unavailable");
        }
        System.out.println(output.getAbsolutePath());
        String text = new PerformanceTextRenderer(PerformanceThresholds.defaults()).render(snapshot);
        Files.write(new File(parent, "performance-preview.txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
        BufferedImage overlay = renderer.renderOverlay(snapshot);
        ImageIO.write(overlay, "png", new File(parent, "performance-ui-transparent.png"));
        BufferedImage neutral = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = neutral.createGraphics();
        try {
            // A flat proof surface, not an alternate wallpaper design.
            graphics.setColor(new Color(42, 57, 73));
            graphics.fillRect(0, 0, 1200, 900);
            graphics.drawImage(overlay, 0, 0, null);
        } finally { graphics.dispose(); }
        ImageIO.write(neutral, "png", new File(parent, "performance-ui-neutral.png"));
    }
}
