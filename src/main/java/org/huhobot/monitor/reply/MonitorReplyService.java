package org.huhobot.monitor.reply;

import org.huhobot.monitor.api.ImageReplyResult;
import org.huhobot.monitor.config.ReplyConfig;
import org.huhobot.monitor.model.PerformanceSnapshot;
import org.huhobot.monitor.render.PerformanceTextRenderer;

import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Selects the configured response mode; text mode cannot invoke the image sender. */
public final class MonitorReplyService {
    @FunctionalInterface
    public interface ImageSender { ImageReplyResult send() throws Exception; }

    private final ReplyConfig config;
    private final PerformanceTextRenderer textRenderer;
    private final Logger logger;
    private final String failureMessage;

    public MonitorReplyService(ReplyConfig config, PerformanceTextRenderer textRenderer,
                               Logger logger, String failureMessage) {
        this.config = config;
        this.textRenderer = textRenderer;
        this.logger = logger;
        this.failureMessage = failureMessage;
    }

    public boolean reply(PerformanceSnapshot snapshot, ImageSender imageSender, Predicate<String> textSender) {
        if (config.isTextMode()) return sendText(textRenderer.render(snapshot), textSender);
        // Initialization already logged the image failure; honor the configured fallback.
        if (imageSender == null) {
            return sendText(config.isFallbackToText() ? textRenderer.render(snapshot) : failureMessage, textSender);
        }
        try {
            ImageReplyResult result = imageSender.send();
            if (result.isSuccess()) return true;
            logger.log(Level.WARNING, "状态图片发送失败，transport=" + result.getTransport()
                + "：" + result.getMessage(), result.getCause());
        } catch (Throwable error) {
            logger.log(Level.WARNING, "状态图片生成或发送失败", error);
        }
        return sendText(config.isFallbackToText() ? textRenderer.render(snapshot) : failureMessage, textSender);
    }

    private boolean sendText(String text, Predicate<String> sender) {
        try {
            if (sender.test(text)) return true;
            logger.warning("状态文字回复失败，未重复发送");
        } catch (Throwable error) {
            logger.log(Level.WARNING, "状态文字回复失败，未重复发送", error);
        }
        return false;
    }
}
