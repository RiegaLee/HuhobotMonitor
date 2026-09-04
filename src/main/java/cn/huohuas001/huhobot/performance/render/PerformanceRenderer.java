package cn.huohuas001.huhobot.performance.render;

import cn.huohuas001.huhobot.performance.model.MetricStatus;
import cn.huohuas001.huhobot.performance.model.PerformanceHealth;
import cn.huohuas001.huhobot.performance.model.PerformanceSnapshot;
import cn.huohuas001.huhobot.performance.model.PerformanceThresholds;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Draws the original HuHoBot "runtime core" information architecture. */
public final class PerformanceRenderer {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 900;

    private static final Color TEXT_PRIMARY = new Color(228, 246, 251);
    private static final Color TEXT_SECONDARY = new Color(195, 222, 233);
    private static final Color TEXT_VALUE = new Color(164, 236, 248);
    private static final Color CYAN = new Color(91, 231, 241);
    private static final Color WARNING = new Color(255, 191, 76);
    private static final Color CRITICAL = new Color(255, 103, 116);
    private static final Color UNAVAILABLE = new Color(175, 207, 219);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"));

    private final String fontFamily;
    private final PerformanceThresholds thresholds;
    private final BufferedImage lightingMap;
    private final BufferedImage headerBotIcon;
    private final BufferedImage statusBotIcon;

    public PerformanceRenderer(String configuredFont) {
        this(configuredFont, PerformanceThresholds.defaults());
    }

    public PerformanceRenderer(String configuredFont, PerformanceThresholds thresholds) {
        this.fontFamily = chooseFont(configuredFont);
        this.thresholds = thresholds == null ? PerformanceThresholds.defaults() : thresholds;
        this.lightingMap = loadImage("/assets/runtime-core-lighting.png");
        this.headerBotIcon = loadImage("/assets/runtime-core-bot-header.png");
        this.statusBotIcon = loadImage("/assets/runtime-core-bot-status.png");
    }

    public byte[] render(PerformanceSnapshot snapshot) throws IOException {
        BufferedImage image = renderImage(snapshot);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer is unavailable");
            return output.toByteArray();
        }
    }

    public BufferedImage renderImage(PerformanceSnapshot snapshot) {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            configure(graphics);
            drawBackdrop(graphics);
            drawGlassSurface(graphics);
            drawHeader(graphics, snapshot);
            drawRuntimeCore(graphics, snapshot);
            drawTelemetry(graphics, snapshot);
            drawDiagnosis(graphics, snapshot);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private void drawBackdrop(Graphics2D graphics) {
        graphics.drawImage(lightingMap, 0, 0, WIDTH, HEIGHT, null);
        // Small optical blooms are layered over the low-frequency approved
        // lighting map; their soft edges survive without carrying old text.
        drawBokeh(graphics, 64, 33, 105, new Color(135, 226, 248, 32));
        drawBokeh(graphics, 425, 44, 120, new Color(135, 229, 250, 35));
        drawBokeh(graphics, 988, 32, 115, new Color(145, 230, 250, 38));
        drawBokeh(graphics, 104, 274, 78, new Color(148, 227, 246, 30));
        drawBokeh(graphics, 991, 229, 96, new Color(145, 229, 250, 34));
        drawBokeh(graphics, 1110, 341, 82, new Color(125, 216, 245, 27));
        drawBokeh(graphics, 1032, 643, 145, new Color(112, 204, 237, 24));
        drawBokeh(graphics, 71, 871, 125, new Color(93, 192, 229, 25));
    }

    private void drawBokeh(Graphics2D graphics, int centerX, int centerY, int diameter, Color centerColor) {
        float radius = diameter / 2f;
        Paint previous = graphics.getPaint();
        graphics.setPaint(new RadialGradientPaint(
            new Point(centerX, centerY), radius,
            new float[]{0f, 0.55f, 1f},
            new Color[]{
                centerColor,
                new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(), 14),
                new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(), 0)
            }
        ));
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, diameter, diameter));
        graphics.setPaint(previous);
    }

    private void drawGlassSurface(Graphics2D graphics) {
        RoundRectangle2D surface = new RoundRectangle2D.Float(37, 53, 1126, 794, 45, 45);
        graphics.setColor(new Color(2, 28, 53, 58));
        graphics.fill(new RoundRectangle2D.Float(40, 61, 1122, 795, 45, 45));

        Paint previous = graphics.getPaint();
        graphics.setPaint(new LinearGradientPaint(
            0, 53, 0, 847,
            new float[]{0f, 0.46f, 1f},
            new Color[]{
                new Color(205, 236, 248, 54),
                new Color(174, 221, 238, 39),
                new Color(122, 184, 214, 33)
            }
        ));
        graphics.fill(surface);

        // Broad, soft blooms supply the diffused light visible through the glass.
        graphics.setPaint(new RadialGradientPaint(
            new Point(595, 359), 490,
            new float[]{0f, 0.48f, 1f},
            new Color[]{new Color(210, 243, 252, 52), new Color(169, 227, 243, 23), new Color(112, 193, 224, 0)}
        ));
        graphics.fill(surface);
        graphics.setPaint(new RadialGradientPaint(
            new Point(191, 169), 300,
            new float[]{0f, 1f},
            new Color[]{new Color(225, 248, 253, 35), new Color(180, 229, 243, 0)}
        ));
        graphics.fill(surface);
        graphics.setPaint(previous);

        for (int glow = 12; glow >= 4; glow -= 4) {
            graphics.setStroke(new BasicStroke(glow));
            graphics.setColor(new Color(213, 246, 253, 9 + (12 - glow) * 2));
            graphics.draw(surface);
        }
        graphics.setStroke(new BasicStroke(2.1f));
        graphics.setColor(new Color(232, 251, 255, 214));
        graphics.draw(surface);
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(new Color(120, 211, 236, 117));
        graphics.draw(new RoundRectangle2D.Float(42, 58, 1116, 784, 40, 40));
    }

    private void drawHeader(Graphics2D graphics, PerformanceSnapshot snapshot) {
        graphics.drawImage(headerBotIcon, 68, 80, 78, 56, null);

        graphics.setFont(font(Font.BOLD, 25));
        graphics.setColor(TEXT_PRIMARY);
        graphics.drawString("HuHoBot", 157, 116);
        int brandWidth = graphics.getFontMetrics().stringWidth("HuHoBot");
        graphics.setFont(font(Font.PLAIN, 23));
        graphics.setColor(TEXT_SECONDARY);
        graphics.drawString(" / 服务器状态", 157 + brandWidth, 116);

        graphics.setFont(font(Font.PLAIN, 19));
        drawRight(graphics, "更新 " + TIME.format(snapshot.getCapturedAt()), 1114, 115, TEXT_SECONDARY);

        graphics.setColor(new Color(197, 244, 252, 90));
        graphics.setStroke(new BasicStroke(1.2f));
        Path2D divider = new Path2D.Float();
        divider.moveTo(38, 143);
        divider.lineTo(516, 143);
        divider.lineTo(529, 154);
        divider.lineTo(671, 154);
        divider.lineTo(684, 143);
        divider.lineTo(1162, 143);
        graphics.draw(divider);

        graphics.setColor(CYAN);
        for (int index = 0; index < 4; index++) {
            graphics.fillRoundRect(579 + index * 15, 142, 7, 7, 2, 2);
        }
    }

    private void drawRuntimeCore(Graphics2D graphics, PerformanceSnapshot snapshot) {
        int centerX = 337;
        int centerY = 441;
        int outerRadius = 218;
        int innerRadius = 166;
        MetricStatus overall = PerformanceHealth.overall(snapshot, thresholds);
        Color accent = statusColor(overall);

        graphics.setStroke(new BasicStroke(1.2f));
        graphics.setColor(new Color(230, 251, 255, 160));
        graphics.draw(new Ellipse2D.Float(
            centerX - outerRadius, centerY - outerRadius, outerRadius * 2, outerRadius * 2
        ));

        Paint previous = graphics.getPaint();
        graphics.setPaint(new RadialGradientPaint(
            new Point(centerX - 18, centerY - 20), innerRadius,
            new float[]{0f, 0.72f, 1f},
            new Color[]{new Color(10, 55, 88, 92), new Color(10, 65, 98, 58), new Color(177, 229, 242, 18)}
        ));
        graphics.fill(new Ellipse2D.Float(
            centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2
        ));
        graphics.setPaint(previous);
        graphics.setStroke(new BasicStroke(1.4f));
        graphics.setColor(new Color(224, 250, 255, 145));
        graphics.draw(new Ellipse2D.Float(
            centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2
        ));

        double tpsRatio = Double.isNaN(snapshot.getTps()) ? 0.18 : clamp(snapshot.getTps() / 20.0);
        double arcExtent = Math.max(24.0, 180.0 * tpsRatio);
        Arc2D arc = new Arc2D.Double(
            centerX - innerRadius, centerY - innerRadius,
            innerRadius * 2, innerRadius * 2,
            90.0, -arcExtent, Arc2D.OPEN
        );
        for (int glow = 22; glow >= 10; glow -= 4) {
            graphics.setStroke(new BasicStroke(glow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 14));
            graphics.draw(arc);
        }
        graphics.setStroke(new BasicStroke(7.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230));
        graphics.draw(arc);

        graphics.setFont(font(Font.PLAIN, 27));
        graphics.setColor(TEXT_SECONDARY);
        drawCenteredIn(graphics, PerformanceHealth.title(overall), centerX, 371);

        String tps = metric(snapshot.getTps(), 1);
        graphics.setFont(font(Font.BOLD, 69));
        int numberWidth = graphics.getFontMetrics().stringWidth(tps);
        graphics.setFont(font(Font.BOLD, 37));
        int unitWidth = graphics.getFontMetrics().stringWidth(" TPS");
        int valueX = centerX - (numberWidth + unitWidth) / 2;
        graphics.setFont(font(Font.BOLD, 69));
        drawTextGlow(graphics, tps, valueX, 460, CYAN);
        graphics.setColor(TEXT_VALUE);
        graphics.drawString(tps, valueX, 460);
        graphics.setFont(font(Font.BOLD, 37));
        drawTextGlow(graphics, " TPS", valueX + numberWidth, 460, accent);
        graphics.setColor(accent);
        graphics.drawString(" TPS", valueX + numberWidth, 460);

        drawEstimatedMspt(graphics, snapshot, centerX, 521, accent);
    }

    private void drawEstimatedMspt(
        Graphics2D graphics,
        PerformanceSnapshot snapshot,
        int centerX,
        int baseline,
        Color accent
    ) {
        String label = "预估MSPT";
        String value = Double.isNaN(snapshot.getTps()) || snapshot.getTps() <= 0.0
            ? " --"
            : " " + metric(1000.0 / snapshot.getTps(), 1) + " ms";

        Font labelFont = font(Font.PLAIN, 21);
        Font valueFont = font(Font.BOLD, 26);
        graphics.setFont(labelFont);
        int labelWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.setFont(valueFont);
        int valueWidth = graphics.getFontMetrics().stringWidth(value);
        int x = centerX - (labelWidth + valueWidth) / 2;

        graphics.setFont(labelFont);
        graphics.setColor(TEXT_SECONDARY);
        graphics.drawString(label, x, baseline);
        graphics.setFont(valueFont);
        drawTextGlow(graphics, value, x + labelWidth, baseline, accent);
        graphics.setColor(accent);
        graphics.drawString(value, x + labelWidth, baseline);
    }

    private void drawTelemetry(Graphics2D graphics, PerformanceSnapshot snapshot) {
        double memoryRatio = snapshot.getMaxMemoryBytes() <= 0L
            ? Double.NaN
            : (double) snapshot.getUsedMemoryBytes() / snapshot.getMaxMemoryBytes();
        double playerRatio = snapshot.getMaxPlayers() <= 0
            ? Double.NaN
            : (double) snapshot.getOnlinePlayers() / snapshot.getMaxPlayers();

        drawTelemetryRow(graphics, 642, 286, "系统 CPU", percent(snapshot.getSystemCpuPercent()),
            ratio(snapshot.getSystemCpuPercent(), 100.0), PerformanceHealth.systemCpu(snapshot, thresholds));
        drawTelemetryRow(graphics, 642, 384, "进程 CPU", percent(snapshot.getProcessCpuPercent()),
            ratio(snapshot.getProcessCpuPercent(), 100.0), PerformanceHealth.processCpu(snapshot, thresholds));
        drawTelemetryRow(graphics, 642, 482, "运行内存", memory(snapshot), memoryRatio,
            PerformanceHealth.memory(snapshot, thresholds));
        drawTelemetryRow(graphics, 642, 580, "在线玩家", snapshot.getOnlinePlayers() + " / " + snapshot.getMaxPlayers(),
            playerRatio, MetricStatus.NORMAL);
    }

    private void drawTelemetryRow(
        Graphics2D graphics,
        int x,
        int y,
        String label,
        String value,
        double fillRatio,
        MetricStatus status
    ) {
        int right = 1110;
        Color accent = statusColor(status);
        graphics.setFont(font(Font.BOLD, 25));
        graphics.setColor(TEXT_PRIMARY);
        graphics.drawString(label, x, y);
        Color valueColor = status == MetricStatus.NORMAL ? TEXT_VALUE : accent;
        drawRightWithGlow(graphics, value, right, y, valueColor, accent);

        int railY = y + 27;
        int railWidth = right - x;
        graphics.setColor(new Color(220, 248, 253, 62));
        graphics.fillRoundRect(x, railY, railWidth, 2, 2, 2);
        if (!Double.isNaN(fillRatio)) {
            graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 185));
            graphics.fillRoundRect(x, railY - 1, (int) Math.round(railWidth * clamp(fillRatio)), 4, 4, 4);
        }
        if (status == MetricStatus.WARNING || status == MetricStatus.CRITICAL) {
            graphics.setColor(accent);
            graphics.fillOval(x - 18, y - 18, 8, 8);
        }
    }

    private void drawDiagnosis(Graphics2D graphics, PerformanceSnapshot snapshot) {
        RoundRectangle2D strip = new RoundRectangle2D.Float(77, 688, 1046, 110, 23, 23);
        graphics.setColor(new Color(7, 42, 71, 48));
        graphics.fill(new RoundRectangle2D.Float(79, 694, 1042, 110, 23, 23));
        Paint previous = graphics.getPaint();
        graphics.setPaint(new LinearGradientPaint(
            0, 688, 0, 798,
            new float[]{0f, 1f},
            new Color[]{new Color(210, 241, 249, 64), new Color(123, 185, 213, 43)}
        ));
        graphics.fill(strip);
        graphics.setPaint(previous);
        graphics.setStroke(new BasicStroke(1.3f));
        graphics.setColor(new Color(230, 251, 255, 157));
        graphics.draw(strip);

        graphics.drawImage(statusBotIcon, 89, 698, 88, 93, null);
        graphics.setColor(new Color(223, 248, 253, 110));
        graphics.fillRoundRect(192, 723, 2, 49, 2, 2);

        graphics.setFont(font(Font.PLAIN, 22));
        graphics.setColor(new Color(215, 236, 244));
        graphics.drawString(truncate(graphics, PerformanceHealth.diagnosis(snapshot, thresholds), 670), 228, 757);

        Color accent = statusColor(PerformanceHealth.overall(snapshot, thresholds));
        graphics.setColor(accent);
        for (int index = 0; index < 4; index++) {
            graphics.fillRoundRect(988 + index * 19, 739, 10, 10, 2, 2);
        }
    }

    private static BufferedImage loadImage(String resourcePath) {
        try (InputStream input = PerformanceRenderer.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IllegalStateException("Missing renderer asset: " + resourcePath);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalStateException("Unreadable renderer asset: " + resourcePath);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load renderer asset: " + resourcePath, exception);
        }
    }

    private Font font(int style, int size) {
        return new Font(fontFamily, style, size);
    }

    private static Color statusColor(MetricStatus status) {
        switch (status) {
            case WARNING: return WARNING;
            case CRITICAL: return CRITICAL;
            case UNAVAILABLE: return UNAVAILABLE;
            default: return CYAN;
        }
    }

    private static String metric(double value, int decimals) {
        if (Double.isNaN(value)) return "--";
        return String.format(Locale.ROOT, decimals == 0 ? "%.0f" : "%.1f", value);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "--" : metric(value, 0) + "%";
    }

    private static String memory(PerformanceSnapshot snapshot) {
        if (snapshot.getMaxMemoryBytes() <= 0L) return "--";
        return metric(toGiB(snapshot.getUsedMemoryBytes()), 1) + " / "
            + metric(toGiB(snapshot.getMaxMemoryBytes()), 1) + " GB";
    }

    private static double toGiB(long bytes) {
        return bytes / 1024.0 / 1024.0 / 1024.0;
    }

    private static double ratio(double value, double maximum) {
        return Double.isNaN(value) || maximum <= 0.0 ? Double.NaN : value / maximum;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void drawCenteredIn(Graphics2D graphics, String text, int centerX, int baseline) {
        graphics.drawString(text, centerX - graphics.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static void drawRight(Graphics2D graphics, String text, int right, int baseline, Color color) {
        graphics.setColor(color);
        graphics.drawString(text, right - graphics.getFontMetrics().stringWidth(text), baseline);
    }

    private static void drawRightWithGlow(
        Graphics2D graphics,
        String text,
        int right,
        int baseline,
        Color color,
        Color glow
    ) {
        int x = right - graphics.getFontMetrics().stringWidth(text);
        drawTextGlow(graphics, text, x, baseline, glow);
        graphics.setColor(color);
        graphics.drawString(text, x, baseline);
    }

    private static void drawTextGlow(Graphics2D graphics, String text, int x, int baseline, Color glow) {
        graphics.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 24));
        graphics.drawString(text, x - 1, baseline);
        graphics.drawString(text, x + 1, baseline);
        graphics.drawString(text, x, baseline - 1);
        graphics.drawString(text, x, baseline + 1);
    }

    private static String truncate(Graphics2D graphics, String text, int maxWidth) {
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) return text;
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end) + "…") > maxWidth) end--;
        return text.substring(0, end) + "…";
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static String chooseFont(String configured) {
        Set<String> available = new HashSet<String>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ));
        if (configured != null && !configured.trim().isEmpty() && available.contains(configured.trim())) {
            return configured.trim();
        }
        String[] candidates = {
            "Microsoft YaHei", "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "SansSerif"
        };
        for (String candidate : candidates) if (available.contains(candidate)) return candidate;
        return Font.SANS_SERIF;
    }
}
