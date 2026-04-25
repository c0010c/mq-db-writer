package com.example.cdcsync.status;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TopicStatusService {

    private final Map<String, TopicStatusRecord> topicStatuses = new ConcurrentHashMap<>();

    public void upsert(TopicStatusRecord record) {
        topicStatuses.put(record.topic(), record);
    }

    public Optional<TopicStatusRecord> get(String topic) {
        return Optional.ofNullable(topicStatuses.get(topic));
    }

    public Map<String, TopicStatusRecord> snapshot() {
        return Map.copyOf(topicStatuses);
    }
}
