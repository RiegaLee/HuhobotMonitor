package org.huhobot.monitor.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomBackgroundTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAndFitsPngToDashboardDimensions() throws Exception {
        BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, Color.MAGENTA.getRGB());
        }
        Path file = temporaryDirectory.resolve("wallpaper.png");
        ImageIO.write(source, "png", file.toFile());

        BufferedImage cover = CustomBackground.load(file, "cover", 120, 90);
        BufferedImage stretch = CustomBackground.load(file, "stretch", 120, 90);

        assertEquals(120, cover.getWidth());
        assertEquals(90, cover.getHeight());
        assertEquals(Color.MAGENTA.getRGB(), cover.getRGB(60, 45));
        assertEquals(Color.MAGENTA.getRGB(), stretch.getRGB(60, 45));
    }

    @Test
    void rejectsMissingOrUnsupportedInputs() {
        Path missing = temporaryDirectory.resolve("missing.png");
        assertThrows(IllegalArgumentException.class,
            () -> CustomBackground.load(missing, "cover", 1200, 900));

        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class,
            () -> CustomBackground.resize(source, "contain", 1200, 900));
    }
}
