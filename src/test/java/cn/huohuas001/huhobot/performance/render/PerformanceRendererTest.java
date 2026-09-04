package cn.huohuas001.huhobot.performance.render;

import cn.huohuas001.huhobot.performance.model.PerformanceSnapshot;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceRendererTest {
    @Test
    void rendersFixedSizePng() throws Exception {
        byte[] png = new PerformanceRenderer("").render(snapshot(19.9, 12.6, 34, 4.2, 8));
        assertTrue(png.length > 100_000);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(1200, image.getWidth());
        assertEquals(900, image.getHeight());
    }

    @Test
    void rendersUnavailableAndCriticalMetricsWithoutFailure() {
        PerformanceRenderer renderer = new PerformanceRenderer("");
        assertNotNull(renderer.renderImage(snapshot(Double.NaN, Double.NaN, Double.NaN, 0, 0)));
        assertNotNull(renderer.renderImage(snapshot(16.5, 62.0, 96, 7.7, 8)));
    }

    private static PerformanceSnapshot snapshot(
        double tps,
        double mspt,
        double cpu,
        double usedGiB,
        double maxGiB
    ) {
        long gib = 1024L * 1024L * 1024L;
        return new PerformanceSnapshot(
            "main", Instant.parse("2026-09-04T06:36:10Z"), tps, mspt, cpu, cpu / 2.0,
            (long) (usedGiB * gib), (long) (maxGiB * gib), 17, 50
        );
    }
}
