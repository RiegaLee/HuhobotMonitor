package cn.huohuas001.huhobot.performance.model;

/** User-configurable boundaries. Warning and critical are ordered by severity. */
public final class PerformanceThresholds {
    private final double tpsWarningBelow;
    private final double tpsCriticalBelow;
    private final double msptWarningAbove;
    private final double msptCriticalAbove;
    private final double cpuWarningAbove;
    private final double cpuCriticalAbove;
    private final double memoryWarningAbove;
    private final double memoryCriticalAbove;

    public PerformanceThresholds(
        double tpsWarningBelow,
        double tpsCriticalBelow,
        double msptWarningAbove,
        double msptCriticalAbove,
        double cpuWarningAbove,
        double cpuCriticalAbove,
        double memoryWarningAbove,
        double memoryCriticalAbove
    ) {
        this.tpsWarningBelow = Math.max(tpsWarningBelow, tpsCriticalBelow);
        this.tpsCriticalBelow = Math.min(tpsWarningBelow, tpsCriticalBelow);
        this.msptWarningAbove = Math.min(msptWarningAbove, msptCriticalAbove);
        this.msptCriticalAbove = Math.max(msptWarningAbove, msptCriticalAbove);
        this.cpuWarningAbove = Math.min(cpuWarningAbove, cpuCriticalAbove);
        this.cpuCriticalAbove = Math.max(cpuWarningAbove, cpuCriticalAbove);
        this.memoryWarningAbove = normalizeRatio(Math.min(memoryWarningAbove, memoryCriticalAbove));
        this.memoryCriticalAbove = normalizeRatio(Math.max(memoryWarningAbove, memoryCriticalAbove));
    }

    public static PerformanceThresholds defaults() {
        return new PerformanceThresholds(19.5, 18.0, 40.0, 50.0, 75.0, 90.0, 0.75, 0.90);
    }

    public double getTpsWarningBelow() {
        return tpsWarningBelow;
    }

    public double getTpsCriticalBelow() {
        return tpsCriticalBelow;
    }

    public double getMsptWarningAbove() {
        return msptWarningAbove;
    }

    public double getMsptCriticalAbove() {
        return msptCriticalAbove;
    }

    public double getCpuWarningAbove() {
        return cpuWarningAbove;
    }

    public double getCpuCriticalAbove() {
        return cpuCriticalAbove;
    }

    public double getMemoryWarningAbove() {
        return memoryWarningAbove;
    }

    public double getMemoryCriticalAbove() {
        return memoryCriticalAbove;
    }

    private static double normalizeRatio(double value) {
        double ratio = value > 1.0 ? value / 100.0 : value;
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}
