package com.example.cdcsync.sync;

public record TopicBatchContext(
        String topic,
        String datasource,
        String targetTable,
        String primaryKey,
        String consumerGroup,
        int batchSize
) {
}
