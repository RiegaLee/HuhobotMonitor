package org.huhobot.monitor.render;

import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceRendererTest {
    @TempDir
    Path temporaryDirectory;

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

    @Test
    void keepsWallpaperBelowIndependentInformationCards() throws Exception {
        BufferedImage wallpaper = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < wallpaper.getHeight(); y++) {
            for (int x = 0; x < wallpaper.getWidth(); x++) wallpaper.setRGB(x, y, Color.RED.getRGB());
        }
        Path path = temporaryDirectory.resolve("wallpaper.png");
        ImageIO.write(wallpaper, "png", path.toFile());

        PerformanceRenderer renderer = new PerformanceRenderer(
            "", PerformanceThresholds.defaults(), path, "cover", 0.12, 0, false, 0.38
        );
        PerformanceSnapshot data = snapshot(19.9, 12.6, 34, 4.2, 8);
        BufferedImage rendered = renderer.renderImage(data);
        // Between the core and widgets the original wallpaper must remain exactly unchanged.
        assertEquals(Color.RED.getRGB(), rendered.getRGB(570, 400));
        BufferedImage overlay = renderer.renderOverlay(data);
        assertEquals(0, overlay.getRGB(570, 400) >>> 24);
        // A clear part of a widget contains only the low-opacity light material.
        assertTrue((overlay.getRGB(730, 250) >>> 24) >= 30);
        assertTrue((overlay.getRGB(730, 250) >>> 24) <= 38);
        assertTrue(new Color(rendered.getRGB(730, 250)).getRed() > 200);
        assertTrue((overlay.getRGB(600, 150) >>> 24) >= 30);
        assertTrue((overlay.getRGB(600, 150) >>> 24) <= 38);
        assertEquals(0, overlay.getRGB(78, 138) >>> 24);
        assertEquals(0, overlay.getRGB(600, 216) >>> 24);
    }

    @Test
    void blursOnlyGlassInteriorsAndKeepsTextLayerIdentical() throws Exception {
        BufferedImage wallpaper = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 900; y++) {
            for (int x = 0; x < 1200; x++) {
                wallpaper.setRGB(x, y, (x / 3 % 2 == 0 ? Color.WHITE : Color.BLACK).getRGB());
            }
        }
        Path path = temporaryDirectory.resolve("striped.png");
        ImageIO.write(wallpaper, "png", path.toFile());
        PerformanceRenderer clear = new PerformanceRenderer("", PerformanceThresholds.defaults(), path, "cover", 0.16, 0, false, 0.46);
        PerformanceRenderer glass = new PerformanceRenderer("", PerformanceThresholds.defaults(), path, "cover", 0.16, 24, false, 0.46);
        PerformanceSnapshot data = snapshot(19.9, 12.6, 34, 4.2, 8);
        BufferedImage original = clear.renderImage(data), blurred = glass.renderImage(data);
        assertEquals(original.getRGB(570, 400), blurred.getRGB(570, 400));
        // Rounded corner outside the card stays clear, not a rectangular blur patch.
        assertEquals(original.getRGB(612, 234), blurred.getRGB(612, 234));
        int r = new Color(blurred.getRGB(730, 250)).getRed();
        assertTrue(r > 70 && r < 160, "Stripes should blend inside the card");
        assertTrue(Math.abs(r - new Color(original.getRGB(730, 250)).getRed()) > 50);
        int headerRed = new Color(blurred.getRGB(600, 150)).getRed();
        assertTrue(headerRed > 70 && headerRed < 160, "Title bar should share the glass blur");
        assertTrue(Math.abs(headerRed - new Color(original.getRGB(600, 150)).getRed()) > 50);
        assertEquals(original.getRGB(78, 138), blurred.getRGB(78, 138));
        BufferedImage clearOverlay = clear.renderOverlay(data), glassOverlay = glass.renderOverlay(data);
        for (int y = 0; y < 900; y++) {
            for (int x = 0; x < 1200; x++) {
                if (clearOverlay.getRGB(x, y) != glassOverlay.getRGB(x, y)) {
                    throw new AssertionError("Glass must not change the text/tint layer");
                }
            }
        }
    }

    @Test
    void blurClampsImageEdgesAndValidatesRadius() {
        BufferedImage solid = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 12; x++) solid.setRGB(x, y, Color.RED.getRGB());
        BufferedImage blurred = BackdropBlur.apply(solid, 24);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 12; x++) {
            Color pixel = new Color(blurred.getRGB(x, y), true);
            assertTrue(pixel.getAlpha() >= 253 && pixel.getRed() >= 253);
            assertEquals(0, pixel.getGreen());
            assertEquals(0, pixel.getBlue());
        }
        assertThrows(IllegalArgumentException.class, () -> BackdropBlur.apply(solid, -1));
        assertThrows(IllegalArgumentException.class, () -> BackdropBlur.apply(solid, 65));
    }

    @Test
    void adaptiveBackdropDimsBrightWallpaperWithoutChangingCardTransparency() throws Exception {
        Path white = solidWallpaper("white.png", Color.WHITE);
        Path black = solidWallpaper("black.png", Color.BLACK);
        PerformanceSnapshot data = snapshot(19.9, 12.6, 34, 4.2, 8);
        PerformanceRenderer bright = new PerformanceRenderer("", PerformanceThresholds.defaults(),
            white, "cover", 0.12, 0, true, 0.38);
        PerformanceRenderer dark = new PerformanceRenderer("", PerformanceThresholds.defaults(),
            black, "cover", 0.12, 0, true, 0.38);
        PerformanceRenderer fixed = new PerformanceRenderer("", PerformanceThresholds.defaults(),
            white, "cover", 0.12, 0, false, 0.38);
        int brightAlpha = bright.renderOverlay(data).getRGB(730, 250) >>> 24;
        int darkAlpha = dark.renderOverlay(data).getRGB(730, 250) >>> 24;
        int fixedAlpha = fixed.renderOverlay(data).getRGB(730, 250) >>> 24;
        assertEquals(brightAlpha, darkAlpha);
        assertEquals(brightAlpha, fixedAlpha);
        assertTrue(new Color(bright.renderImage(data).getRGB(570, 400)).getRed() < 200);
        assertEquals(Color.WHITE.getRGB(), fixed.renderImage(data).getRGB(570, 400));
    }

    @Test
    void transparentCardMaterialDoesNotVaryAcrossWallpaperRegions() throws Exception {
        BufferedImage wallpaper = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1200; x++) {
            wallpaper.setRGB(x, y, (x < 870 ? Color.WHITE : Color.BLACK).getRGB());
        }
        Path path = temporaryDirectory.resolve("split.png");
        ImageIO.write(wallpaper, "png", path.toFile());
        PerformanceRenderer renderer = new PerformanceRenderer("", PerformanceThresholds.defaults(),
            path, "cover", 0.12, 0, true, 0.38);
        BufferedImage overlay = renderer.renderOverlay(snapshot(19.9, 12.6, 34, 4.2, 8));
        int brightCardAlpha = overlay.getRGB(730, 250) >>> 24;
        int darkCardAlpha = overlay.getRGB(1000, 250) >>> 24;
        assertTrue(brightCardAlpha >= 30 && brightCardAlpha <= 38);
        assertEquals(brightCardAlpha, darkCardAlpha);
    }

    @Test
    void validatesMaximumBackdropDimming() throws Exception {
        Path wallpaper = solidWallpaper("gray.png", Color.GRAY);
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRenderer("",
            PerformanceThresholds.defaults(), wallpaper, "cover", 0.16, 24, true, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRenderer("",
            PerformanceThresholds.defaults(), wallpaper, "cover", 0.16, 24, true, 1.1));
        assertNotNull(new PerformanceRenderer("", PerformanceThresholds.defaults(),
            wallpaper, "cover", 0.70, 24, true, 0.46).renderImage(snapshot(20, 10, 10, 1, 2)));
    }

    @Test
    void longServerNameStaysOnRightWithoutChangingTitle() {
        PerformanceRenderer renderer = new PerformanceRenderer("");
        PerformanceSnapshot shortName = snapshot(19.9, 12.6, 34, 4.2, 8);
        PerformanceSnapshot longName = new PerformanceSnapshot(
            "这是一个很长的服务器名称用于验证标题栏右对齐以及超长省略显示不会覆盖左侧服务器状态标题",
            Instant.parse("2026-09-04T06:36:10Z"), 19.9, 12.6, 34, 17,
            (long) (4.2 * 1073741824L), 8L * 1073741824L, 17, 50);
        BufferedImage first = renderer.renderOverlay(shortName), second = renderer.renderOverlay(longName);
        int changedPixels = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1200; x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) {
                changedPixels++;
                assertTrue(x >= 530 && x <= 1093 && y >= 136 && y < 208,
                    "Server name must stay in the right-hand title area");
            }
        }
        assertTrue(changedPixels > 0);
    }

    @Test
    void keepsTopClearAndPlacesTimestampAtSummaryRight() {
        PerformanceRenderer renderer = new PerformanceRenderer("");
        BufferedImage first = renderer.renderOverlay(snapshot(19.9, 12.6, 34, 4.2, 8));
        for (int y = 0; y < 130; y++) for (int x = 0; x < 1200; x++) {
            assertEquals(0, first.getRGB(x, y) >>> 24, "No branding or timestamp above the title bar");
        }
        PerformanceSnapshot later = new PerformanceSnapshot(
            "main", Instant.parse("2026-09-04T08:42:59Z"), 19.9, 12.6, 34, 17,
            (long) (4.2 * 1073741824L), 8L * 1073741824L, 17, 50);
        BufferedImage second = renderer.renderOverlay(later);
        int changedPixels = 0;
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1200; x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) {
                changedPixels++;
                assertTrue(x >= 900 && x <= 1093 && y >= 750 && y < 790,
                    "Timestamp must remain inside the right end of the summary bar");
            }
        }
        assertTrue(changedPixels > 0);
    }

    private Path solidWallpaper(String name, Color color) throws Exception {
        BufferedImage wallpaper = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) wallpaper.setRGB(x, y, color.getRGB());
        Path path = temporaryDirectory.resolve(name);
        ImageIO.write(wallpaper, "png", path.toFile());
        return path;
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
