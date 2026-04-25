package com.example.cdcsync.sync;

public record TopicConsumerConfig(
        String topic,
        String consumerGroup,
        int batchSize,
        int consumeThreadMin,
        int consumeThreadMax
) {
}
