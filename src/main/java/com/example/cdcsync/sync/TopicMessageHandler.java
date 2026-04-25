package com.example.cdcsync.sync;

@FunctionalInterface
public interface TopicMessageHandler {

    void handle(TopicBatchContext batchContext, ConsumedMessage message, int indexInBatch);
}
