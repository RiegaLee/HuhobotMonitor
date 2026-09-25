package org.huhobot.monitor.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceHealthTest {
    private final PerformanceThresholds thresholds = PerformanceThresholds.defaults();

    @Test
    void classifiesHealthyWarningAndCriticalSnapshots() {
        assertEquals(MetricStatus.NORMAL, PerformanceHealth.overall(snapshot(19.9, 12.6, 34, 0.52), thresholds));
        assertEquals(MetricStatus.WARNING, PerformanceHealth.overall(snapshot(19.0, 43.0, 78, 0.80), thresholds));
        assertEquals(MetricStatus.CRITICAL, PerformanceHealth.overall(snapshot(16.8, 59.0, 94, 0.94), thresholds));
    }

    @Test
    void usesPlayerFriendlyMemoryWording() {
        String diagnosis = PerformanceHealth.diagnosis(snapshot(20.0, 10.0, 20, 0.95), thresholds);
        assertTrue(diagnosis.contains("运行内存"));
    }

    @Test
    void missingMsptDoesNotMakeOtherwiseHealthyServerLookBroken() {
        PerformanceSnapshot snapshot = snapshot(19.9, Double.NaN, 30, 0.50);
        assertEquals(MetricStatus.NORMAL, PerformanceHealth.overall(snapshot, thresholds));
        assertTrue(PerformanceHealth.diagnosis(snapshot, thresholds).contains("未提供真实 MSPT"));
    }

    private static PerformanceSnapshot snapshot(double tps, double mspt, double cpu, double memoryRatio) {
        long max = 8L * 1024L * 1024L * 1024L;
        return new PerformanceSnapshot(
            "test", Instant.EPOCH, tps, mspt, cpu, cpu / 2.0,
            (long) (max * memoryRatio), max, 3, 20
        );
    }
}
