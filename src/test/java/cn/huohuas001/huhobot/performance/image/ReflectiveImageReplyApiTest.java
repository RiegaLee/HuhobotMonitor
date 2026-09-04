package cn.huohuas001.huhobot.performance.image;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.huhobot.performance.api.ImageReplyRequest;
import cn.huohuas001.huhobot.performance.api.ImageReplyResult;
import io.github.kloping.qqbot.entities.ex.msg.MessageChain;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void uploadsThroughMainBranchSdkFallback() {
        FakeGroupBase groupBase = new FakeGroupBase();
        QClient.setStarter(new FakeStarter(new FakeBot(groupBase)));
        ReflectiveImageReplyApi api = new ReflectiveImageReplyApi(new Object(), Logger.getLogger("test"), 1024);
        ImageReplyResult result = api.reply(new ImageReplyRequest(
            null, "group-open-id", "message-id", 12, " ", "server-performance.png", new byte[]{1, 2, 3}
        ));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("qq-sdk-file-data", result.getTransport());
        assertTrue(groupBase.uploadJson.contains("\"file_data\":\"AQID\""));
        assertFalse(groupBase.uploadJson.contains("http://"));
        assertTrue(groupBase.messageJson.contains("\"msg_id\":\"message-id\""));
        assertTrue(groupBase.messageJson.contains("\"msg_seq\":12"));
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

    public static final class FakeStarter {
        private final FakeBot bot;

        private FakeStarter(FakeBot bot) {
            this.bot = bot;
        }

        public FakeBot getBot() {
            return bot;
        }
    }

    public static final class FakeBot {
        public final FakeGroupBase groupBaseV2;

        private FakeBot(FakeGroupBase groupBaseV2) {
            this.groupBaseV2 = groupBaseV2;
        }
    }

    public static final class FakeGroupBase {
        private String uploadJson;
        private String messageJson;

        public FakeUploadResult sendFile(String group, String json, Map<?, ?> headers) {
            uploadJson = json;
            return new FakeUploadResult("uploaded-file-info");
        }

        public Object send(String group, String json, Map<?, ?> headers) {
            messageJson = json;
            return new Object();
        }
    }

    public static final class FakeUploadResult {
        private final String fileInfo;

        private FakeUploadResult(String fileInfo) {
            this.fileInfo = fileInfo;
        }

        public String getFile_info() {
            return fileInfo;
        }
    }
}
