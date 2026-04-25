package com.example.cdcsync.sync;

public interface OrderedConsumer {

    void start() throws Exception;

    void shutdown();
}
