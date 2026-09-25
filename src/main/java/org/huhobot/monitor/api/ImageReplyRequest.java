package org.huhobot.monitor.api;

import java.util.Arrays;
import java.util.Objects;

public final class ImageReplyRequest {
    private final Object sourceEvent;
    private final String groupOpenId;
    private final String messageId;
    private final int messageSequence;
    private final String text;
    private final String filename;
    private final byte[] imageBytes;

    public ImageReplyRequest(
        Object sourceEvent,
        String groupOpenId,
        String messageId,
        int messageSequence,
        String text,
        String filename,
        byte[] imageBytes
    ) {
        this.sourceEvent = sourceEvent;
        this.groupOpenId = requireText(groupOpenId, "groupOpenId");
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.messageSequence = messageSequence;
        this.text = text == null ? "" : text;
        this.filename = requireText(filename, "filename");
        this.imageBytes = Arrays.copyOf(Objects.requireNonNull(imageBytes, "imageBytes"), imageBytes.length);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }

    public Object getSourceEvent() {
        return sourceEvent;
    }

    public String getGroupOpenId() {
        return groupOpenId;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getMessageSequence() {
        return messageSequence;
    }

    public String getText() {
        return text;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getImageBytes() {
        return Arrays.copyOf(imageBytes, imageBytes.length);
    }

    public int getImageSize() {
        return imageBytes.length;
    }
}
