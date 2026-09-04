package cn.huohuas001.huhobot.performance.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickRateSamplerTest {
    @Test
    void reportsUnavailableDuringWarmup() {
        TickRateSampler sampler = new TickRateSampler();
        for (int tick = 0; tick < 10; tick++) sampler.record(tick * 50_000_000L);
        assertTrue(Double.isNaN(sampler.oneMinuteTps()));
    }

    @Test
    void measuresNormalAndLaggingTickRates() {
        TickRateSampler normal = new TickRateSampler();
        for (int tick = 0; tick < 1200; tick++) normal.record(tick * 50_000_000L);
        assertEquals(20.0, normal.oneMinuteTps(), 0.01);

        TickRateSampler lagging = new TickRateSampler();
        for (int tick = 0; tick < 600; tick++) lagging.record(tick * 100_000_000L);
        assertEquals(10.0, lagging.oneMinuteTps(), 0.01);
    }
}
