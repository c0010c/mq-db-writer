package com.example.cdcsync.sync;

import java.util.Arrays;

public record ConsumedMessage(
        String messageId,
        String keys,
        int queueId,
        long queueOffset,
        byte[] body
) {

    public byte[] bodyCopy() {
        return body == null ? null : Arrays.copyOf(body, body.length);
    }
}
