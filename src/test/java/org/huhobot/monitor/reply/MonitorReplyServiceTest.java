package org.huhobot.monitor.reply;

import org.huhobot.monitor.api.ImageReplyResult;
import org.huhobot.monitor.config.ReplyConfig;
import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.model.PerformanceThresholds;
import org.huhobot.monitor.render.PerformanceTextRenderer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class MonitorReplyServiceTest {
    private final PerformanceSnapshot snapshot = new PerformanceSnapshot("main", Instant.EPOCH,
        20, 12, 30, 15, 1024, 4096, 1, 10);
    private final List<String> texts = new ArrayList<String>();

    @Test void textModeNeverCallsImagePipeline() {
        assertTrue(service("text", true).reply(snapshot, () -> {
            fail("Text mode must not render or upload images"); return null;
        }, texts::add));
        assertEquals(1, texts.size());
        assertTrue(texts.get(0).startsWith("服务器状态"));
    }

    @Test void configuredTextModeSkipsImagesEvenWhenFallbackDisabled() {
        assertTrue(service("text", false).reply(snapshot, () -> {
            fail("Configured text mode must not render or upload images"); return null;
        }, texts::add));
        assertEquals(1, texts.size());
    }

    @Test void successfulImageDoesNotSendText() {
        assertTrue(service("image", true).reply(snapshot,
            () -> ImageReplyResult.success("test"), texts::add));
        assertTrue(texts.isEmpty());
    }

    @Test void uploadFailureFallsBackOnceWithSnapshot() {
        AtomicInteger attempts = new AtomicInteger();
        assertTrue(service("image", true).reply(snapshot, () -> {
            attempts.incrementAndGet();
            return ImageReplyResult.failure("test", "upload rejected", null);
        }, texts::add));
        assertEquals(1, attempts.get());
        assertEquals(1, texts.size());
        assertTrue(texts.get(0).contains("TPS：20.0"));
    }

    @Test void renderExceptionFallsBackOnce() {
        assertTrue(service("image", true).reply(snapshot, () -> {
            throw new IOException("render failed");
        }, texts::add));
        assertEquals(1, texts.size());
        assertTrue(texts.get(0).startsWith("服务器状态"));
    }

    @Test void disabledFallbackSendsOnlyFailureMessage() {
        assertTrue(service("image", false).reply(snapshot,
            () -> ImageReplyResult.failure("test", "failed", null), texts::add));
        assertEquals(1, texts.size());
        assertEquals("failed", texts.get(0));
    }

    @Test void textFailureOrExceptionDoesNotRetryOrAttemptImages() {
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(service("text", true).reply(snapshot, null, text -> {
            attempts.incrementAndGet(); return false;
        }));
        assertEquals(1, attempts.get());
        assertFalse(service("text", true).reply(snapshot, null, text -> {
            attempts.incrementAndGet(); throw new IllegalStateException("network error");
        }));
        assertEquals(2, attempts.get());
    }

    @Test void unavailableImagesFollowConfiguredFallback() {
        assertTrue(service("image", true).reply(snapshot, null, texts::add));
        assertTrue(texts.get(0).startsWith("服务器状态"));
        assertTrue(service("image", false).reply(snapshot, null, texts::add));
        assertEquals("failed", texts.get(1));
        assertEquals(2, texts.size());
    }

    private MonitorReplyService service(String mode, boolean fallback) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reply.mode", mode);
        yaml.set("reply.fallback-to-text", fallback);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new MonitorReplyService(ReplyConfig.load(yaml),
            new PerformanceTextRenderer(PerformanceThresholds.defaults()), logger, "failed");
    }
}
