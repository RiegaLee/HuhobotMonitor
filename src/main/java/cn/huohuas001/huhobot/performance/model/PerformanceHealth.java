package cn.huohuas001.huhobot.performance.model;

/** Converts raw metrics into a small, consistent HuHoBot diagnosis. */
public final class PerformanceHealth {
    private PerformanceHealth() {
    }

    public static MetricStatus tps(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        double value = snapshot.getTps();
        if (Double.isNaN(value)) return MetricStatus.UNAVAILABLE;
        if (value < thresholds.getTpsCriticalBelow()) return MetricStatus.CRITICAL;
        if (value < thresholds.getTpsWarningBelow()) return MetricStatus.WARNING;
        return MetricStatus.NORMAL;
    }

    public static MetricStatus mspt(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        double value = snapshot.getMspt();
        if (Double.isNaN(value)) return MetricStatus.UNAVAILABLE;
        if (value > thresholds.getMsptCriticalAbove()) return MetricStatus.CRITICAL;
        if (value > thresholds.getMsptWarningAbove()) return MetricStatus.WARNING;
        return MetricStatus.NORMAL;
    }

    public static MetricStatus systemCpu(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        return cpu(snapshot.getSystemCpuPercent(), thresholds);
    }

    public static MetricStatus processCpu(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        return cpu(snapshot.getProcessCpuPercent(), thresholds);
    }

    public static MetricStatus memory(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        if (snapshot.getMaxMemoryBytes() <= 0L) return MetricStatus.UNAVAILABLE;
        double ratio = (double) snapshot.getUsedMemoryBytes() / snapshot.getMaxMemoryBytes();
        if (ratio > thresholds.getMemoryCriticalAbove()) return MetricStatus.CRITICAL;
        if (ratio > thresholds.getMemoryWarningAbove()) return MetricStatus.WARNING;
        return MetricStatus.NORMAL;
    }

    public static MetricStatus overall(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        MetricStatus status = MetricStatus.UNAVAILABLE;
        status = worse(status, tps(snapshot, thresholds));
        status = worse(status, mspt(snapshot, thresholds));
        status = worse(status, systemCpu(snapshot, thresholds));
        status = worse(status, processCpu(snapshot, thresholds));
        status = worse(status, memory(snapshot, thresholds));
        return status;
    }

    public static String title(MetricStatus status) {
        switch (status) {
            case CRITICAL: return "负载较高";
            case WARNING: return "轻微波动";
            case UNAVAILABLE: return "正在采样";
            default: return "运行稳定";
        }
    }

    public static String diagnosis(PerformanceSnapshot snapshot, PerformanceThresholds thresholds) {
        MetricStatus overall = overall(snapshot, thresholds);
        if (overall == MetricStatus.CRITICAL) {
            if (tps(snapshot, thresholds) == MetricStatus.CRITICAL || mspt(snapshot, thresholds) == MetricStatus.CRITICAL) {
                return "主线程负载较高，建议检查耗时任务与实体数量";
            }
            if (memory(snapshot, thresholds) == MetricStatus.CRITICAL) {
                return "运行内存占用较高，建议检查内存配置与插件缓存";
            }
            return "服务器负载较高，建议尽快检查运行状态";
        }
        if (overall == MetricStatus.WARNING) return "检测到轻微性能波动，建议继续观察";
        if (overall == MetricStatus.UNAVAILABLE) return "监控刚刚启动，正在收集服务器运行数据";
        if (Double.isNaN(snapshot.getMspt())) return "服务器运行平稳；当前平台未提供真实 MSPT，状态参考 TPS";
        return "主线程余量充足，服务器运行平稳";
    }

    private static MetricStatus cpu(double value, PerformanceThresholds thresholds) {
        if (Double.isNaN(value)) return MetricStatus.UNAVAILABLE;
        if (value > thresholds.getCpuCriticalAbove()) return MetricStatus.CRITICAL;
        if (value > thresholds.getCpuWarningAbove()) return MetricStatus.WARNING;
        return MetricStatus.NORMAL;
    }

    private static MetricStatus worse(MetricStatus current, MetricStatus candidate) {
        if (severity(candidate) > severity(current)) return candidate;
        if (current == MetricStatus.UNAVAILABLE && candidate == MetricStatus.NORMAL) return MetricStatus.NORMAL;
        return current;
    }

    private static int severity(MetricStatus status) {
        switch (status) {
            case CRITICAL: return 3;
            case WARNING: return 2;
            case NORMAL: return 1;
            default: return 0;
        }
    }
}
