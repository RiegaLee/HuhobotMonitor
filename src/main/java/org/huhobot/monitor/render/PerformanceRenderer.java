package org.huhobot.monitor.render;

import org.huhobot.monitor.model.MetricStatus;
import org.huhobot.monitor.model.PerformanceHealth;
import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Wallpaper and transparent information layer are rendered independently. */
public final class PerformanceRenderer {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 900;
    private static final Color WHITE = new Color(246, 250, 252);
    private static final Color MUTED = new Color(224, 235, 241, 205);
    private static final Color MINT = new Color(145, 242, 215);
    private static final double TARGET_BACKDROP_LUMINANCE = 0.56;
    private static final double MINIMUM_ADAPTIVE_DIMMING = 0.06;
    public static final int DEFAULT_GLASS_BLUR_RADIUS = 0;
    private static final Shape HEADER_SURFACE = new RoundRectangle2D.Double(76, 136, 1048, 72, 72, 72);
    private static final Shape CORE_SURFACE = new Ellipse2D.Double(107, 225, 414, 414);
    private static final Shape SUMMARY_SURFACE = new RoundRectangle2D.Double(76, 724, 1048, 88, 88, 88);
    private static final Shape SYSTEM_CPU_SURFACE = new RoundRectangle2D.Double(610, 232, 245, 210, 100, 100);
    private static final Shape PROCESS_CPU_SURFACE = new RoundRectangle2D.Double(879, 232, 245, 210, 100, 100);
    private static final Shape MEMORY_SURFACE = new RoundRectangle2D.Double(610, 465, 245, 210, 100, 100);
    private static final Shape PLAYERS_SURFACE = new RoundRectangle2D.Double(879, 465, 245, 210, 100, 100);
    private static final Shape[] ALL_SURFACES = {
        HEADER_SURFACE, CORE_SURFACE, SYSTEM_CPU_SURFACE, PROCESS_CPU_SURFACE,
        MEMORY_SURFACE, PLAYERS_SURFACE, SUMMARY_SURFACE
    };
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"));

    private final String fontFamily;
    private final String numberFamily;
    private final PerformanceThresholds thresholds;
    private final BufferedImage backdrop;
    private final double surfaceOpacity;

    public PerformanceRenderer(String font) {
        this(font, PerformanceThresholds.defaults());
    }

    public PerformanceRenderer(String font, PerformanceThresholds thresholds) {
        this(font, thresholds, null, "cover");
    }

    public PerformanceRenderer(String font, PerformanceThresholds thresholds, Path background, String fit) {
        this(font, thresholds, background, fit, 0.12);
    }

    public PerformanceRenderer(String font, PerformanceThresholds thresholds, Path background,
                               String fit, double opacity) {
        this(font, thresholds, background, fit, opacity, DEFAULT_GLASS_BLUR_RADIUS);
    }

    public PerformanceRenderer(String font, PerformanceThresholds thresholds, Path background,
                               String fit, double opacity, int blurRadius) {
        this(font, thresholds, background, fit, opacity, blurRadius, true, 0.38);
    }

    public PerformanceRenderer(String font, PerformanceThresholds thresholds, Path background,
                               String fit, double opacity, int blurRadius,
                               boolean adaptiveBackdropDimming, double maximumBackdropDimming) {
        this.fontFamily = chooseFont(font, true);
        this.numberFamily = chooseFont(font, false);
        this.thresholds = thresholds == null ? PerformanceThresholds.defaults() : thresholds;
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("render.surface-opacity must be between 0 and 1");
        }
        if (!Double.isFinite(maximumBackdropDimming) || maximumBackdropDimming < 0 || maximumBackdropDimming > 1) {
            throw new IllegalArgumentException("render.maximum-backdrop-dimming must be between 0 and 1");
        }
        this.surfaceOpacity = opacity;
        BufferedImage source = background == null ? loadDefaultBackground()
            : CustomBackground.load(background, fit, WIDTH, HEIGHT);
        // Wallpaper is static: calculate optional blur and the global veil only once.
        BufferedImage materialBackdrop = glassBackdrop(source, blurRadius);
        this.backdrop = adaptiveBackdropDimming
            ? dimBackdrop(materialBackdrop, source, maximumBackdropDimming)
            : materialBackdrop;
    }

    public byte[] render(PerformanceSnapshot snapshot) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(renderImage(snapshot), "png", output)) {
                throw new IOException("PNG writer is unavailable");
            }
            return output.toByteArray();
        }
    }

    public BufferedImage renderImage(PerformanceSnapshot snapshot) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.drawImage(backdrop, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(renderOverlay(snapshot), 0, 0, null);
        } finally { g.dispose(); }
        return image;
    }

    /** Text and light smoke surfaces. Wallpaper adaptation lives in the backdrop, not the cards. */
    public BufferedImage renderOverlay(PerformanceSnapshot snapshot) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            configure(g);
            drawHeader(g, snapshot);
            drawCore(g, snapshot);
            drawMetrics(g, snapshot);
            drawSummary(g, snapshot);
        } finally { g.dispose(); }
        return image;
    }

    private void drawHeader(Graphics2D g, PerformanceSnapshot s) {
        surface(g, HEADER_SURFACE);
        text(g, "服务器状态", 108, 182, 28, Font.BOLD, WHITE);
        g.setFont(font(24, Font.PLAIN));
        String name = truncate(g, s.getServerName(), 560);
        right(g, name, 1092, 182, WHITE);
    }

    private void drawCore(Graphics2D g, PerformanceSnapshot s) {
        final int cx = 314, cy = 432, radius = 184;
        Color accent = color(PerformanceHealth.overall(s, thresholds));
        // The main datum has one circular surface, with a single open progress ring.
        surface(g, CORE_SURFACE);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 38));
        g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2,
            230, -280, Arc2D.OPEN));
        if (Double.isFinite(s.getTps())) {
            g.setColor(accent);
            g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2,
                230, -280 * clamp(s.getTps() / 20), Arc2D.OPEN));
        }
        centered(g, "服务器 TPS", cx, 337, 20, Font.PLAIN, MUTED);
        g.setFont(numberFont(108));
        centeredCurrent(g, metric(s.getTps(), 1), cx, 455, WHITE);
        centered(g, PerformanceHealth.title(PerformanceHealth.overall(s, thresholds)),
            cx, 500, 21, Font.PLAIN, accent);
        String mspt = s.getTps() > 0 ? metric(1000 / s.getTps(), 1) + " ms" : "--";
        centered(g, "预估 MSPT  ·  " + mspt, cx, 548, 17, Font.PLAIN, MUTED);
        centered(g, "TICKS PER SECOND", cx, 615, 12, Font.PLAIN, MUTED);
    }

    private void drawMetrics(Graphics2D g, PerformanceSnapshot s) {
        metricCard(g, 610, 232, "系统 CPU", metric(s.getSystemCpuPercent(), 0), "%",
            "整机处理器占用", s.getSystemCpuPercent() / 100, PerformanceHealth.systemCpu(s, thresholds));
        metricCard(g, 879, 232, "进程 CPU", metric(s.getProcessCpuPercent(), 0), "%",
            "Java 进程占用", s.getProcessCpuPercent() / 100, PerformanceHealth.processCpu(s, thresholds));
        double memoryRatio = s.getMaxMemoryBytes() == 0 ? Double.NaN
            : (double) s.getUsedMemoryBytes() / s.getMaxMemoryBytes();
        metricCard(g, 610, 465, "运行内存",
            s.getMaxMemoryBytes() == 0 ? "--" : metric(gib(s.getUsedMemoryBytes()), 1), "GB",
            "上限 " + (s.getMaxMemoryBytes() == 0 ? "--" : metric(gib(s.getMaxMemoryBytes()), 1)) + " GB",
            memoryRatio, PerformanceHealth.memory(s, thresholds));
        metricCard(g, 879, 465, "在线玩家", Integer.toString(s.getOnlinePlayers()), "人",
            "容量 " + s.getMaxPlayers() + " 人",
            s.getMaxPlayers() <= 0 ? Double.NaN : (double) s.getOnlinePlayers() / s.getMaxPlayers(),
            MetricStatus.NORMAL);
    }

    private void metricCard(Graphics2D g, int x, int y, String label, String value, String unit,
                            String detail, double ratio, MetricStatus status) {
        // 50px radius on a 210px-high widget: visibly soft shoulders, without button-like pills.
        surface(g, metricSurface(x, y));
        text(g, label, x + 30, y + 43, 19, Font.PLAIN, MUTED);
        Color accent = color(status);
        g.setColor(accent);
        g.fillOval(x + 201, y + 29, 6, 6);
        int valueSize = value.length() > 4 ? 44 : 58;
        g.setFont(numberFont(valueSize));
        drawTextWithShadow(g, value, x + 28, y + 119,
            status == MetricStatus.NORMAL ? WHITE : accent);
        int valueWidth = g.getFontMetrics().stringWidth(value);
        text(g, unit, x + 36 + valueWidth, y + 118, 18, Font.PLAIN, MUTED);
        text(g, detail, x + 31, y + 155, 15, Font.PLAIN, MUTED);
        g.setColor(new Color(255, 255, 255, 36));
        g.fillRoundRect(x + 31, y + 177, 183, 3, 3, 3);
        if (Double.isFinite(ratio)) {
            g.setColor(accent);
            g.fillRoundRect(x + 31, y + 177, (int) Math.round(183 * clamp(ratio)), 3, 3, 3);
        }
    }

    private void drawSummary(Graphics2D g, PerformanceSnapshot s) {
        surface(g, SUMMARY_SURFACE);
        Color accent = color(PerformanceHealth.overall(s, thresholds));
        g.setColor(accent);
        g.fillOval(108, 763, 10, 10);
        text(g, "运行诊断", 136, 775, 18, Font.BOLD, WHITE);
        g.setColor(new Color(255, 255, 255, 42));
        g.setStroke(new BasicStroke(1));
        g.drawLine(245, 751, 245, 785);
        String generatedAt = "生成于 " + TIME.format(s.getCapturedAt());
        g.setFont(font(16, Font.PLAIN));
        int timeWidth = g.getFontMetrics().stringWidth(generatedAt);
        right(g, generatedAt, 1092, 775, MUTED);
        g.setFont(font(18, Font.PLAIN));
        drawTextWithShadow(g, truncate(g, PerformanceHealth.diagnosis(s, thresholds),
            1092 - timeWidth - 32 - 273), 273, 775, MUTED);
    }

    private static Shape metricSurface(int x, int y) {
        if (x == 610 && y == 232) return SYSTEM_CPU_SURFACE;
        if (x == 879 && y == 232) return PROCESS_CPU_SURFACE;
        if (x == 610 && y == 465) return MEMORY_SURFACE;
        if (x == 879 && y == 465) return PLAYERS_SURFACE;
        return new RoundRectangle2D.Double(x, y, 245, 210, 100, 100);
    }

    private static BufferedImage glassBackdrop(BufferedImage source, int radius) {
        BufferedImage blurred = BackdropBlur.apply(source, radius);
        if (radius == 0) return source;
        BufferedImage mask = surfaceMask();
        BufferedImage glass = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D layer = glass.createGraphics();
        try {
            layer.drawImage(blurred, 0, 0, WIDTH, HEIGHT, null);
            layer.setComposite(AlphaComposite.DstIn);
            layer.drawImage(mask, 0, 0, null);
        } finally { layer.dispose(); }
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(glass, 0, 0, null);
        } finally { g.dispose(); }
        return result;
    }

    private static BufferedImage surfaceMask() {
        BufferedImage mask = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        try {
            configure(g);
            g.setColor(Color.WHITE);
            for (Shape shape : ALL_SURFACES) g.fill(shape);
        } finally { g.dispose(); }
        return mask;
    }

    private static BufferedImage dimBackdrop(BufferedImage backdrop, BufferedImage source, double maximum) {
        double opacity = adaptiveBackdropDimming(source, maximum);
        if (opacity <= 0) return backdrop;
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.drawImage(backdrop, 0, 0, null);
            g.setColor(new Color(20, 22, 26, (int) Math.round(255 * opacity)));
            g.fillRect(0, 0, WIDTH, HEIGHT);
        } finally { g.dispose(); }
        return result;
    }

    private static double adaptiveBackdropDimming(BufferedImage image, double maximum) {
        if (maximum <= 0) return 0;
        int capacity = ((image.getWidth() + 7) / 8) * ((image.getHeight() + 7) / 8);
        double[] samples = new double[capacity];
        int count = 0;
        for (int y = 4; y < image.getHeight(); y += 8) {
            for (int x = 4; x < image.getWidth(); x += 8) {
                int rgb = image.getRGB(x, y);
                samples[count++] = (0.2126 * ((rgb >>> 16) & 255)
                    + 0.7152 * ((rgb >>> 8) & 255) + 0.0722 * (rgb & 255)) / 255.0;
            }
        }
        Arrays.sort(samples, 0, count);
        double brightTail = samples[Math.min(count - 1, (int) Math.floor((count - 1) * 0.85))];
        double tintLuminance = (0.2126 * 20 + 0.7152 * 22 + 0.0722 * 26) / 255.0;
        double required = brightTail <= TARGET_BACKDROP_LUMINANCE ? MINIMUM_ADAPTIVE_DIMMING
            : (brightTail - TARGET_BACKDROP_LUMINANCE) / (brightTail - tintLuminance);
        return Math.min(maximum, Math.max(Math.min(MINIMUM_ADAPTIVE_DIMMING, maximum), required));
    }

    private void surface(Graphics2D g, Shape shape) {
        Rectangle bounds = shape.getBounds();
        int topAlpha = (int) Math.round(255 * Math.min(1, surfaceOpacity * 1.15));
        int bottomAlpha = (int) Math.round(255 * Math.min(1, surfaceOpacity * 0.65));
        g.setPaint(new GradientPaint(0, bounds.y,
            new Color(244, 247, 249, topAlpha), 0, bounds.y + bounds.height,
            new Color(214, 221, 226, bottomAlpha)));
        g.fill(shape);
        // A brighter rim carries the material edge while the wallpaper remains visible inside.
        if (surfaceOpacity > 0) {
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(255, 255, 255, 58));
            g.draw(shape);
        }
    }

    private Font font(int size, int weight) { return new Font(fontFamily, weight, size); }
    private Font numberFont(int size) { return new Font(numberFamily, Font.PLAIN, size); }

    private void text(Graphics2D g, String text, int x, int y, int size, int weight, Color color) {
        g.setFont(font(size, weight));
        drawTextWithShadow(g, text, x, y, color);
    }

    private void centered(Graphics2D g, String text, int x, int y, int size, int weight, Color color) {
        g.setFont(font(size, weight));
        centeredCurrent(g, text, x, y, color);
    }

    private static void centeredCurrent(Graphics2D g, String text, int x, int y, Color color) {
        drawTextWithShadow(g, text, x - g.getFontMetrics().stringWidth(text) / 2, y, color);
    }

    private static void right(Graphics2D g, String text, int x, int y, Color color) {
        drawTextWithShadow(g, text, x - g.getFontMetrics().stringWidth(text), y, color);
    }

    private static void drawTextWithShadow(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 92));
        g.drawString(text, x + 1, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static Color color(MetricStatus status) {
        switch (status) {
            case WARNING: return new Color(255, 205, 130);
            case CRITICAL: return new Color(255, 153, 164);
            case UNAVAILABLE: return new Color(198, 210, 220);
            default: return MINT;
        }
    }

    private static String metric(double value, int decimals) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, decimals == 0 ? "%.0f" : "%.1f", value) : "--";
    }
    private static double gib(long bytes) { return bytes / 1073741824.0; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    private static String truncate(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "…") > width) end--;
        return text.substring(0, end) + "…";
    }

    private static String chooseFont(String configured, boolean chinese) {
        Set<String> available = new HashSet<String>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH)));
        if (configured != null && !configured.trim().isEmpty()) {
            Font candidate = new Font(configured.trim(), Font.PLAIN, 20);
            if (!candidate.getFamily(Locale.ENGLISH).equals("Dialog") &&
                (!chinese || candidate.canDisplayUpTo("服务器状态") == -1)) return configured.trim();
        }
        String[] names = chinese
            ? new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei"}
            : new String[]{"Segoe UI", "Inter", "Noto Sans", "Arial"};
        for (String name : names) if (available.contains(name)) return name;
        return Font.SANS_SERIF;
    }

    private static BufferedImage loadDefaultBackground() {
        try (InputStream in = PerformanceRenderer.class.getResourceAsStream("/assets/runtime-core-lighting.png")) {
            if (in == null) throw new IllegalStateException("Missing default performance background");
            BufferedImage result = ImageIO.read(in);
            if (result == null) throw new IllegalStateException("Invalid default performance background");
            return result;
        } catch (IOException e) { throw new IllegalStateException("Cannot load performance background", e); }
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
