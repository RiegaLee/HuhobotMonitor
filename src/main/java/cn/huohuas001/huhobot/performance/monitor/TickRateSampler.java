package cn.huohuas001.huhobot.performance.monitor;

import java.util.ArrayDeque;
import java.util.Deque;

/** A platform-independent rolling TPS fallback driven once per server tick. */
public final class TickRateSampler {
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_SAMPLES = 1400;

    private final Deque<Long> ticks = new ArrayDeque<Long>();

    public synchronized void record(long nowNanos) {
        ticks.addLast(nowNanos);
        while (ticks.size() > MAX_SAMPLES || (!ticks.isEmpty() && nowNanos - ticks.getFirst() > WINDOW_NANOS)) {
            ticks.removeFirst();
        }
    }

    public synchronized double oneMinuteTps() {
        if (ticks.size() < 20) return Double.NaN;
        long elapsed = ticks.getLast() - ticks.getFirst();
        if (elapsed <= 0L) return Double.NaN;
        return Math.min(20.0, (ticks.size() - 1) * 1_000_000_000.0 / elapsed);
    }

    public synchronized int sampleCount() {
        return ticks.size();
    }

    public synchronized void clear() {
        ticks.clear();
    }
}
