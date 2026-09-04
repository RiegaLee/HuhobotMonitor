package cn.huohuas001.huhobot.performance.monitor;

import cn.huohuas001.huhobot.performance.model.PerformanceSnapshot;
import cn.huohuas001.huhobot.performance.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.logging.Logger;

/** Collects metrics without a hard Paper or NMS dependency. */
public final class PerformanceMonitor {
    private final JavaPlugin owner;
    private final Logger logger;
    private final TickRateSampler tickSampler = new TickRateSampler();
    private BukkitTask tickTask;
    private boolean msptFallbackLogged;

    public PerformanceMonitor(JavaPlugin owner) {
        this.owner = owner;
        this.logger = owner.getLogger();
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = owner.getServer().getScheduler().runTaskTimer(
            owner,
            () -> tickSampler.record(System.nanoTime()),
            1L,
            1L
        );
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        tickSampler.clear();
    }

    /** Must be called from the Bukkit main thread because it reads player state. */
    public PerformanceSnapshot capture(String serverName) {
        Server server = Bukkit.getServer();
        Object internalServer = readInternalServer(server);
        double tps = firstAvailable(readTps(server), readTps(internalServer), tickSampler.oneMinuteTps());
        double mspt = firstAvailable(readAverageTickTime(server), readAverageTickTime(internalServer));
        if (Double.isNaN(mspt) && !msptFallbackLogged) {
            msptFallbackLogged = true;
            logger.info("当前服务端未公开真实 MSPT；健康判断将参考 TPS，界面仍显示明确标注的预估 MSPT");
        }

        return new PerformanceSnapshot(
            serverName,
            Instant.now(),
            cap(tps, 20.0),
            mspt,
            systemCpuPercent(),
            processCpuPercent(),
            usedMemoryBytes(),
            Runtime.getRuntime().maxMemory(),
            Bukkit.getOnlinePlayers().size(),
            Bukkit.getMaxPlayers()
        );
    }

    private static Object readInternalServer(Server server) {
        try {
            return Reflect.invoke(server, "getServer");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double readTps(Object target) {
        if (target == null) return Double.NaN;
        try {
            Object value;
            try {
                value = Reflect.invoke(target, "getTPS");
            } catch (ReflectiveOperationException ignored) {
                value = Reflect.read(target, "recentTps");
            }
            if (value instanceof double[] && ((double[]) value).length > 0) return ((double[]) value)[0];
            if (value instanceof Number) return ((Number) value).doubleValue();
        } catch (Throwable ignored) {
            // The scheduler sampler is the stable Spigot fallback.
        }
        return Double.NaN;
    }

    private static double readAverageTickTime(Object target) {
        if (target == null) return Double.NaN;
        try {
            Object value = Reflect.read(target, "averageTickTime");
            if (value instanceof Number) return plausibleMspt(((Number) value).doubleValue());
        } catch (Throwable ignored) {
            // Try the tick time ring below.
        }
        try {
            Object value = Reflect.read(target, "tickTimes");
            if (value instanceof long[]) return averageNanos((long[]) value);
        } catch (Throwable ignored) {
            // Pure Spigot versions without stable tick-time fields report unavailable.
        }
        return Double.NaN;
    }

    private static double averageNanos(long[] values) {
        if (values.length == 0) return Double.NaN;
        double sum = 0.0;
        int count = 0;
        for (long value : values) {
            if (value <= 0L) continue;
            sum += value / 1_000_000.0;
            count++;
        }
        return count == 0 ? Double.NaN : plausibleMspt(sum / count);
    }

    private static double plausibleMspt(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 60_000.0
            ? Double.NaN
            : value;
    }

    private static double systemCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (!(bean instanceof com.sun.management.OperatingSystemMXBean)) return Double.NaN;
        double load = ((com.sun.management.OperatingSystemMXBean) bean).getSystemCpuLoad();
        return load < 0.0 ? Double.NaN : cap(load * 100.0, 100.0);
    }

    private static double processCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (!(bean instanceof com.sun.management.OperatingSystemMXBean)) return Double.NaN;
        double load = ((com.sun.management.OperatingSystemMXBean) bean).getProcessCpuLoad();
        return load < 0.0 ? Double.NaN : cap(load * 100.0, 100.0);
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static double firstAvailable(double... values) {
        for (double value : values) if (!Double.isNaN(value) && !Double.isInfinite(value)) return value;
        return Double.NaN;
    }

    private static double cap(double value, double maximum) {
        return Double.isNaN(value) ? Double.NaN : Math.max(0.0, Math.min(maximum, value));
    }
}
