package org.huhobot.monitor.render;

import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceTextRendererTest {
    private final PerformanceTextRenderer renderer = new PerformanceTextRenderer(PerformanceThresholds.defaults());

    @Test void includesAllMetricsAndActualMspt() {
        String text = renderer.render(snapshot(19.9, 12.6, 34, 8L * 1073741824));
        assertEquals("服务器状态 · main\n状态：运行稳定\nTPS：19.9 / 20\nMSPT：12.6 ms"
            + "\n系统 CPU：34.0%\n进程 CPU：17.0%\n运行内存：4.2 / 8.0 GiB"
            + "\n在线玩家：17 / 50\n运行诊断：主线程余量充足，服务器运行平稳\n生成于 14:36:10", text);
    }

    @Test void unavailableMetricsHavePlaceholdersAndNeverNanOrInfinity() {
        String text = renderer.render(snapshot(Double.NaN, Double.NaN, Double.NaN, 0));
        assertTrue(text.contains("状态：正在采样"));
        assertTrue(text.contains("TPS：--"));
        assertTrue(text.contains("预估 MSPT：-- ms"));
        assertTrue(text.contains("运行内存：--"));
        assertFalse(text.contains("NaN"));
        assertFalse(text.contains("Infinity"));
        assertTrue(renderer.render(snapshot(0, Double.NaN, 20, 0)).contains("预估 MSPT：-- ms"));
    }

    @Test void estimatesOnlyWhenRealMsptIsUnavailableAndKeepsDiagnosis() {
        String estimated = renderer.render(snapshot(20, Double.NaN, 34, 8L * 1073741824));
        assertTrue(estimated.contains("预估 MSPT：50.0 ms"));
        String critical = renderer.render(snapshot(16, 62, 95, 8L * 1073741824));
        assertTrue(critical.contains("状态：负载较高"));
        assertTrue(critical.contains("主线程负载较高"));
    }

    private static PerformanceSnapshot snapshot(double tps, double mspt, double cpu, long maxMemory) {
        return new PerformanceSnapshot("main", Instant.parse("2026-09-04T06:36:10Z"),
            tps, mspt, cpu, cpu / 2, (long) (4.2 * 1073741824), maxMemory, 17, 50);
    }
}
