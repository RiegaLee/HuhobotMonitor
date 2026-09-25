package org.huhobot.monitor.image;

import org.huhobot.monitor.api.ImageReplyRequest;
import org.huhobot.monitor.api.ImageReplyResult;
import io.github.kloping.qqbot.entities.ex.msg.MessageChain;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveImageReplyApiTest {
    @Test
    void prefersNativeByteMethod() {
        NativeEvent event = new NativeEvent();
        byte[] bytes = {1, 2, 3};
        ReflectiveImageReplyApi api = new ReflectiveImageReplyApi(new Object(), Logger.getLogger("test"), 1024);
        ImageReplyResult result = api.reply(request(event, bytes));
        assertTrue(result.isSuccess());
        assertEquals("event-native:replyImage", result.getTransport());
        assertArrayEquals(bytes, event.received);
    }

    @Test
    void sendsRawAgentEventWithMessageChain() {
        RawGroupEvent event = new RawGroupEvent();
        ReflectiveImageReplyApi api = new ReflectiveImageReplyApi(new Object(), Logger.getLogger("test"), 1024);
        ImageReplyResult result = api.reply(request(event, new byte[]{9, 8, 7}));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("qq-sdk-message-chain", result.getTransport());
        assertArrayEquals(new byte[]{9, 8, 7}, event.received.getImage());
    }

    @Test
    void rejectsImagesAboveConfiguredLimit() {
        ReflectiveImageReplyApi api = new ReflectiveImageReplyApi(new Object(), Logger.getLogger("test"), 1024);
        ImageReplyResult result = api.reply(new ImageReplyRequest(
            null, "group-open-id", "message-id", 12, " ", "server-performance.png", new byte[1025]
        ));
        assertTrue(!result.isSuccess());
        assertEquals("validation", result.getTransport());
    }

    private static ImageReplyRequest request(Object event, byte[] bytes) {
        return new ImageReplyRequest(event, "group", "message", 7, " ", "server-performance.png", bytes);
    }

    public static final class NativeEvent {
        private byte[] received;

        public boolean replyImage(byte[] bytes) {
            received = bytes;
            return true;
        }
    }

    public static final class RawGroupEvent {
        private MessageChain received;

        public Object sendMessage(MessageChain chain) {
            received = chain;
            return new Object();
        }
    }

}
