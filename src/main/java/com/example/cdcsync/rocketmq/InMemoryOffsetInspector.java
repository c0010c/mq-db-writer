package com.example.cdcsync.rocketmq;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOffsetInspector implements OffsetInspector {

    private final Set<String> availableOffsets = ConcurrentHashMap.newKeySet();

    @Override
    public boolean hasOffset(String topic, String consumerGroup) {
        return availableOffsets.contains(key(topic, consumerGroup));
    }

    public void addOffset(String topic, String consumerGroup) {
        availableOffsets.add(key(topic, consumerGroup));
    }

    private String key(String topic, String consumerGroup) {
        return topic + "@@" + consumerGroup;
    }
}
