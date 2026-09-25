package org.huhobot.monitor.render;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/** Separable Gaussian blur with clamped edges and premultiplied alpha. */
final class BackdropBlur {
    private BackdropBlur() { }

    static BufferedImage apply(BufferedImage source, int radius) {
        if (radius < 0 || radius > 64) {
            throw new IllegalArgumentException("render.glass-blur-radius must be between 0 and 64");
        }
        if (radius == 0) return source;
        int width = source.getWidth(), height = source.getHeight();
        BufferedImage padded = new BufferedImage(width + radius * 2, height + radius * 2,
            BufferedImage.TYPE_INT_ARGB_PRE);
        for (int y = 0; y < padded.getHeight(); y++) {
            int sy = Math.max(0, Math.min(height - 1, y - radius));
            for (int x = 0; x < padded.getWidth(); x++) {
                int sx = Math.max(0, Math.min(width - 1, x - radius));
                padded.setRGB(x, y, source.getRGB(sx, sy));
            }
        }
        float[] weights = new float[radius * 2 + 1];
        double sigma = Math.max(1, radius / 3.0), sum = 0;
        for (int i = -radius; i <= radius; i++) {
            weights[i + radius] = (float) Math.exp(-i * i / (2 * sigma * sigma));
            sum += weights[i + radius];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        BufferedImage horizontal = new ConvolveOp(new Kernel(weights.length, 1, weights),
            ConvolveOp.EDGE_NO_OP, null).filter(padded, null);
        BufferedImage vertical = new ConvolveOp(new Kernel(1, weights.length, weights),
            ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = result.createGraphics();
        try { g.drawImage(vertical, -radius, -radius, null); }
        finally { g.dispose(); }
        return result;
    }
}
