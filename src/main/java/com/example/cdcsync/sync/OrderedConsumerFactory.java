package com.example.cdcsync.sync;

public interface OrderedConsumerFactory {

    OrderedConsumer create(TopicConsumerConfig config, OrderedMessageListener listener) throws Exception;
}
