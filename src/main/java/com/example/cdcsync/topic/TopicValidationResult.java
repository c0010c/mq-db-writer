package com.example.cdcsync.topic;

import com.example.cdcsync.status.TopicStatus;

public record TopicValidationResult(
        String topic,
        String consumerGroup,
        TopicStatus status,
        String errorMessage
) {
}
