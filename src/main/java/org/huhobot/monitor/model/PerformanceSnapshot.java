package org.huhobot.monitor.model;

import java.time.Instant;

/** A point-in-time, renderer-friendly view of one server's health. NaN means unavailable. */
public final class PerformanceSnapshot {
    private final String serverName;
    private final Instant capturedAt;
    private final double tps;
    private final double mspt;
    private final double systemCpuPercent;
    private final double processCpuPercent;
    private final long usedMemoryBytes;
    private final long maxMemoryBytes;
    private final int onlinePlayers;
    private final int maxPlayers;

    public PerformanceSnapshot(
        String serverName,
        Instant capturedAt,
        double tps,
        double mspt,
        double systemCpuPercent,
        double processCpuPercent,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int onlinePlayers,
        int maxPlayers
    ) {
        this.serverName = serverName == null ? "server" : serverName;
        this.capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        this.tps = sanitizeMetric(tps);
        this.mspt = sanitizeMetric(mspt);
        this.systemCpuPercent = sanitizePercent(systemCpuPercent);
        this.processCpuPercent = sanitizePercent(processCpuPercent);
        this.usedMemoryBytes = Math.max(0L, usedMemoryBytes);
        this.maxMemoryBytes = Math.max(0L, maxMemoryBytes);
        this.onlinePlayers = Math.max(0, onlinePlayers);
        this.maxPlayers = Math.max(0, maxPlayers);
    }

    public String getServerName() {
        return serverName;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public double getTps() {
        return tps;
    }

    public double getMspt() {
        return mspt;
    }

    public double getSystemCpuPercent() {
        return systemCpuPercent;
    }

    public double getProcessCpuPercent() {
        return processCpuPercent;
    }

    public long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    private static double sanitizeMetric(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 ? Double.NaN : value;
    }

    private static double sanitizePercent(double value) {
        double metric = sanitizeMetric(value);
        return Double.isNaN(metric) ? Double.NaN : Math.min(100.0, metric);
    }
}
