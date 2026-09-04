package cn.huohuas001.huhobot.performance.bridge;

import cn.huohuas001.huhobot.performance.util.Reflect;

/** Stable command context extracted from either HuHoBot branch. */
public final class BotCommandContext {
    private static final String DEFAULT_COMMAND = "性能监控";

    private final Object sourceEvent;
    private final String commandKey;
    private final String commandArguments;
    private final String groupOpenId;
    private final String messageId;
    private final int messageSequence;
    private final boolean cancellable;

    private BotCommandContext(
        Object sourceEvent,
        String commandKey,
        String commandArguments,
        String groupOpenId,
        String messageId,
        int messageSequence,
        boolean cancellable
    ) {
        this.sourceEvent = sourceEvent;
        this.commandKey = commandKey;
        this.commandArguments = commandArguments;
        this.groupOpenId = groupOpenId;
        this.messageId = messageId;
        this.messageSequence = messageSequence;
        this.cancellable = cancellable;
    }

    public static BotCommandContext from(Object event) throws ReflectiveOperationException {
        Object message;
        try {
            message = Reflect.read(event, "message");
        } catch (ReflectiveOperationException ignored) {
            message = Reflect.read(event, "msgPack");
        }
        return new BotCommandContext(
            event,
            text(Reflect.read(message, "commandKey")),
            optionalText(message, "commandArguments"),
            text(Reflect.read(message, "groupOpenId")),
            text(Reflect.read(message, "messageId")),
            number(Reflect.read(message, "messageSequence")),
            true
        );
    }

    public static BotCommandContext fromRawEvent(Object event) throws ReflectiveOperationException {
        Object rawMessage = Reflect.read(event, "rawMessage");
        return fromRawEvent(event, extractNativeArguments(rawMessage), rawMessage);
    }

    private static BotCommandContext fromRawEvent(
        Object event,
        String commandArguments,
        Object rawMessage
    ) throws ReflectiveOperationException {
        String groupOpenId = optionalText(event, "groupOpenId");
        if (groupOpenId.isEmpty()) groupOpenId = optionalText(event, "groupId");
        int sequence = 0;
        try {
            sequence = number(Reflect.read(event, "msgSeq"));
        } catch (ReflectiveOperationException ignored) {
            // MessageChain can select its own sequence.
        }
        return new BotCommandContext(
            event,
            DEFAULT_COMMAND,
            commandArguments == null ? "" : commandArguments.trim(),
            groupOpenId,
            text(Reflect.read(rawMessage, "id")),
            sequence,
            false
        );
    }

    public void cancel() {
        if (!cancellable) return;
        try {
            Reflect.invoke(sourceEvent, "setCancelled", true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("HuHoBot OnBotCommand cannot be cancelled", error);
        }
    }

    public boolean replyText(String text) {
        try {
            Object value = Reflect.invoke(sourceEvent, "replyText", text);
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (ReflectiveOperationException first) {
            try {
                Object value = Reflect.invoke(sourceEvent, "reply", text);
                return !(value instanceof Boolean) || (Boolean) value;
            } catch (ReflectiveOperationException second) {
                try {
                    Object value = Reflect.invoke(sourceEvent, "sendMessage", text);
                    return value != null;
                } catch (ReflectiveOperationException third) {
                    return false;
                }
            }
        }
    }

    public Object getSourceEvent() {
        return sourceEvent;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public String getCommandArguments() {
        return commandArguments;
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

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String optionalText(Object target, String name) {
        try {
            return text(Reflect.read(target, name));
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String extractNativeArguments(Object rawMessage) {
        String content = optionalText(rawMessage, "content").replaceAll("<@!?[^>]+>", "").trim();
        if (content.startsWith("/")) content = content.substring(1).trim();
        if (!content.startsWith(DEFAULT_COMMAND)) return "";
        return content.substring(DEFAULT_COMMAND.length()).trim();
    }
}
