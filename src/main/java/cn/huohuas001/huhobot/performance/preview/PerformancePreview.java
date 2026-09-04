package cn.huohuas001.huhobot.performance.preview;

import cn.huohuas001.huhobot.performance.model.PerformanceSnapshot;
import cn.huohuas001.huhobot.performance.render.PerformanceRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;

/** Generates a deterministic design preview without starting a Minecraft server. */
public final class PerformancePreview {
    private PerformancePreview() {
    }

    public static void main(String[] args) throws Exception {
        File output = new File(args.length == 0 ? "build/preview/performance-preview-v1.png" : args[0]);
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

        BufferedImage image = new PerformanceRenderer("").renderImage(snapshot);
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("PNG writer is unavailable");
        }
        System.out.println(output.getAbsolutePath());
    }
}
