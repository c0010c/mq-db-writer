package com.example.cdcsync.sync;

import java.util.List;

@FunctionalInterface
public interface OrderedMessageListener {

    ConsumeResult consumeMessage(List<ConsumedMessage> messages, ConsumeOrderlyContext context);
}
