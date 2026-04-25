package com.example.cdcsync.status;

public record TopicStatusRecord(
        String topic,
        String datasource,
        String targetTable,
        String primaryKey,
        String consumerGroup,
        TopicStatus status,
        String lastErrorMessage
) {
}
