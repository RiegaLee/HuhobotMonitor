package org.huhobot.monitor.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotCommandContextTest {
    @Test void nullableMessageIdReportsFailureWithoutTryingAlias() throws Exception {
        NullReply event = new NullReply();
        assertFalse(BotCommandContext.fromRawEvent(event).replyText("status"));
        assertEquals(1, event.attempts);
    }

    @Test void thrownSendDoesNotRetryOtherAliases() throws Exception {
        ThrowingReply event = new ThrowingReply();
        assertFalse(BotCommandContext.fromRawEvent(event).replyText("status"));
        assertEquals(1, event.attempts);
    }

    @Test void nativeSendMessageAndVoidReplyAreSupported() throws Exception {
        NativeReply nativeEvent = new NativeReply();
        assertTrue(BotCommandContext.fromRawEvent(nativeEvent).replyText("status"));
        assertEquals("status", nativeEvent.sent);
        VoidReply voidEvent = new VoidReply();
        assertTrue(BotCommandContext.fromRawEvent(voidEvent).replyText("status"));
        assertEquals("status", voidEvent.sent);
    }

    public static class Event {
        public final Raw rawMessage = new Raw();
        public final String groupOpenId = "group";
        public final int msgSeq = 1;
    }
    public static class Raw {
        public final String content = "/服务器状态";
        public final String id = "message";
    }
    public static class NullReply extends Event {
        int attempts;
        public String replyText(String text) { attempts++; return null; }
        public boolean reply(String text) { attempts++; return true; }
    }
    public static class ThrowingReply extends NullReply {
        @Override public String replyText(String text) { attempts++; throw new IllegalStateException("send failed"); }
    }
    public static class NativeReply extends Event {
        String sent;
        public String sendMessage(String text) { sent = text; return "new-message-id"; }
    }
    public static class VoidReply extends Event {
        String sent;
        public void replyText(String text) { sent = text; }
    }
}
