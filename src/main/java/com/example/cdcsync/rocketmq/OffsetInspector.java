package com.example.cdcsync.rocketmq;

public interface OffsetInspector {

    boolean hasOffset(String topic, String consumerGroup);
}
