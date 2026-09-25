package org.huhobot.monitor.image;

import org.huhobot.monitor.api.ImageReplyApi;
import org.huhobot.monitor.api.ImageReplyRequest;
import org.huhobot.monitor.api.ImageReplyResult;
import org.huhobot.monitor.util.JsonStrings;
import org.huhobot.monitor.util.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends image bytes through the best image entry exposed by the active HuHoBot branch. */
public final class ReflectiveImageReplyApi implements ImageReplyApi {
    private final Object huHoBotPlugin;
    private final Logger logger;
    private final int maxBytes;

    public ReflectiveImageReplyApi(Object huHoBotPlugin, Logger logger, int maxBytes) {
        this.huHoBotPlugin = huHoBotPlugin;
        this.logger = logger;
        this.maxBytes = Math.max(1024, maxBytes);
    }

    @Override
    public ImageReplyResult reply(ImageReplyRequest request) {
        if (request.getImageSize() == 0) return ImageReplyResult.failure("validation", "image is empty", null);
        if (request.getImageSize() > maxBytes) {
            return ImageReplyResult.failure("validation", "image is larger than configured max-bytes: "
                + request.getImageSize(), null);
        }

        ImageReplyResult nativeResult = tryNativeEventApi(request);
        if (nativeResult != null && nativeResult.isSuccess()) return nativeResult;
        ImageReplyResult chainResult = tryQqMessageChain(request);
        if (chainResult != null && chainResult.isSuccess()) return chainResult;
        ImageReplyResult sdkResult = tryQqSdk(request);
        if (sdkResult.isSuccess()) return sdkResult;

        if (nativeResult != null) {
            logger.log(Level.FINE, "HuHoBot native image API failed; SDK fallback also failed", nativeResult.getCause());
        }
        return sdkResult;
    }

    private ImageReplyResult tryNativeEventApi(ImageReplyRequest request) {
        Object event = request.getSourceEvent();
        if (event == null) return null;
        byte[] bytes = request.getImageBytes();
        Object[][] attempts = {
            {"replyImage", new Object[]{bytes}},
            {"replyImage", new Object[]{request.getText(), bytes}},
            {"replyWithImage", new Object[]{bytes}},
            {"replyWithImage", new Object[]{request.getText(), bytes}},
            {"replyWithImg", new Object[]{bytes}},
            {"replyWithImg", new Object[]{request.getText(), bytes}}
        };
        for (Object[] attempt : attempts) {
            String methodName = (String) attempt[0];
            Object[] args = (Object[]) attempt[1];
            Method method = Reflect.findCompatibleMethod(event.getClass(), methodName, args);
            if (method == null) continue;
            try {
                Object value = Reflect.invoke(event, methodName, args);
                if (!(value instanceof Boolean) || (Boolean) value) {
                    return ImageReplyResult.success("event-native:" + methodName);
                }
                return ImageReplyResult.failure("event-native:" + methodName, "method returned false", null);
            } catch (Throwable error) {
                return ImageReplyResult.failure("event-native:" + methodName, concise(error), error);
            }
        }
        return null;
    }

    private ImageReplyResult tryQqMessageChain(ImageReplyRequest request) {
        Object event = request.getSourceEvent();
        if (event == null) return null;
        try {
            ClassLoader loader = event.getClass().getClassLoader();
            Class<?> chainClass = Class.forName("io.github.kloping.qqbot.entities.ex.msg.MessageChain", false, loader);
            Object chain = chainClass.getConstructor().newInstance();
            Reflect.invoke(chain, "text", request.getText().isEmpty() ? "[性能监控]" : request.getText());
            Reflect.invoke(chain, "image", request.getImageBytes());
            if (Reflect.findCompatibleMethod(event.getClass(), "sendMessage", chain) == null) return null;
            Reflect.invoke(event, "sendMessage", chain);
            return ImageReplyResult.success("qq-sdk-message-chain");
        } catch (ClassNotFoundException error) {
            return null;
        } catch (Throwable error) {
            return ImageReplyResult.failure("qq-sdk-message-chain", concise(error), error);
        }
    }

    private ImageReplyResult tryQqSdk(ImageReplyRequest request) {
        try {
            ClassLoader loader = huHoBotPlugin.getClass().getClassLoader();
            if (loader == null) loader = ReflectiveImageReplyApi.class.getClassLoader();
            Class<?> qClientClass = Class.forName("cn.huohuas001.bot.QClient", false, loader);
            Object starter = readKotlinLateinit(qClientClass, "starter");
            Object bot = Reflect.invoke(starter, "getBot");
            Object groupBase = readAny(bot, "groupBaseV2", "getGroupBaseV2");
            Map<?, ?> headers = messageHeaders(loader);

            String fileData = Base64.getEncoder().encodeToString(request.getImageBytes());
            String uploadJson = "{\"file_type\":1,\"file_data\":" + JsonStrings.quote(fileData)
                + ",\"srv_send_msg\":false}";
            Object uploadResult = Reflect.invoke(groupBase, "sendFile", request.getGroupOpenId(), uploadJson, headers);
            Object fileInfoValue = readAny(uploadResult, "file_info", "fileInfo", "getFile_info", "getFileInfo");
            String fileInfo = fileInfoValue == null ? "" : fileInfoValue.toString();
            if (fileInfo.trim().isEmpty()) {
                return ImageReplyResult.failure("qq-sdk-file-data", "upload response has no file_info", null);
            }

            StringBuilder json = new StringBuilder(256)
                .append("{\"content\":").append(JsonStrings.quote(request.getText().isEmpty() ? "[性能监控]" : request.getText()))
                .append(",\"msg_type\":7,\"media\":{\"file_info\":").append(JsonStrings.quote(fileInfo)).append('}');
            if (!request.getMessageId().isEmpty()) json.append(",\"msg_id\":").append(JsonStrings.quote(request.getMessageId()));
            json.append(",\"msg_seq\":").append(request.getMessageSequence()).append('}');
            Reflect.invoke(groupBase, "send", request.getGroupOpenId(), json.toString(), headers);
            return ImageReplyResult.success("qq-sdk-file-data");
        } catch (Throwable error) {
            return ImageReplyResult.failure("qq-sdk-file-data", concise(error), error);
        }
    }

    private static Object readKotlinLateinit(Class<?> type, String fieldName) throws ReflectiveOperationException {
        Field field = Reflect.findField(type, fieldName);
        if (field == null) throw new NoSuchFieldException(type.getName() + "." + fieldName);
        field.setAccessible(true);
        Object receiver = Modifier.isStatic(field.getModifiers()) ? null : Reflect.kotlinObject(type);
        Object value = field.get(receiver);
        if (value == null) throw new IllegalStateException("HuHoBot QQ client is not initialized");
        return value;
    }

    private static Object readAny(Object target, String... names) throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) return Reflect.invoke(target, name);
                return Reflect.read(target, name);
            } catch (ReflectiveOperationException error) {
                last = error;
            }
        }
        throw last == null ? new NoSuchFieldException(target.getClass().getName()) : last;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> messageHeaders(ClassLoader loader) {
        try {
            Class<?> channel = Class.forName("io.github.kloping.qqbot.entities.qqpd.Channel", false, loader);
            Object value = Reflect.readStaticField(channel, "SEND_MESSAGE_HEADERS");
            if (value instanceof Map) return (Map<?, ?>) value;
        } catch (Throwable ignored) {
            // Older SDK forks already declare the JSON content type.
        }
        return Collections.singletonMap("Content-Type", "application/json");
    }

    private static String concise(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return cursor.getClass().getSimpleName() + (cursor.getMessage() == null ? "" : ": " + cursor.getMessage());
    }
}
