package org.huhobot.monitor.render;

import org.huhobot.monitor.model.PerformanceHealth;
import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Plain text only: no Java2D, fonts, wallpaper or image encoding. */
public final class PerformanceTextRenderer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"));
    private final PerformanceThresholds thresholds;

    public PerformanceTextRenderer(PerformanceThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public String render(PerformanceSnapshot s) {
        String name = s.getServerName().replaceAll("[\\r\\n\\t]+", " ");
        if (name.length() > 80) name = name.substring(0, 80) + "…";
        String mspt = Double.isFinite(s.getMspt())
            ? "MSPT：" + number(s.getMspt()) + " ms"
            : "预估 MSPT：" + (s.getTps() > 0 ? number(1000 / s.getTps()) : "--") + " ms";
        String memory = s.getMaxMemoryBytes() > 0
            ? number(s.getUsedMemoryBytes() / 1073741824.0) + " / "
                + number(s.getMaxMemoryBytes() / 1073741824.0) + " GiB"
            : "--";
        return "服务器状态 · " + name
            + "\n状态：" + PerformanceHealth.title(PerformanceHealth.overall(s, thresholds))
            + "\nTPS：" + number(s.getTps()) + " / 20"
            + "\n" + mspt
            + "\n系统 CPU：" + number(s.getSystemCpuPercent()) + "%"
            + "\n进程 CPU：" + number(s.getProcessCpuPercent()) + "%"
            + "\n运行内存：" + memory
            + "\n在线玩家：" + s.getOnlinePlayers() + " / " + (s.getMaxPlayers() > 0 ? s.getMaxPlayers() : "--")
            + "\n运行诊断：" + PerformanceHealth.diagnosis(s, thresholds)
            + "\n生成于 " + TIME.format(s.getCapturedAt());
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "--";
    }
}
