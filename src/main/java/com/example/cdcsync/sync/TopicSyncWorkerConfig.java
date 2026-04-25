package com.example.cdcsync.sync;

import com.example.cdcsync.config.model.TopicProperties;

import java.time.Duration;

public record TopicSyncWorkerConfig(
        TopicProperties topic,
        String consumerGroup,
        long suspendCurrentQueueTimeMillis,
        Duration gracefulShutdownTimeout
) {
}
