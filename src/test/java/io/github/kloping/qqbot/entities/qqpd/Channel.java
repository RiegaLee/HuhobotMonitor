package io.github.kloping.qqbot.entities.qqpd;

import java.util.Collections;
import java.util.Map;

public final class Channel {
    public static final Map<String, String> SEND_MESSAGE_HEADERS =
        Collections.singletonMap("Content-Type", "application/json");

    private Channel() {
    }
}
