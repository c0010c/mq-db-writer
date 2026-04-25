package com.example.cdcsync.sync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TopicSyncManager {

    private final Map<String, TopicSyncWorker> workers = new ConcurrentHashMap<>();

    public void register(String topic, TopicSyncWorker worker) {
        workers.put(topic, worker);
    }

    public void start(String topic) throws Exception {
        TopicSyncWorker worker = workers.get(topic);
        if (worker != null) {
            worker.start();
        }
    }

    public boolean stop(String topic) {
        TopicSyncWorker worker = workers.get(topic);
        if (worker == null) {
            return true;
        }
        return worker.stop();
    }

    public void stopAll() {
        workers.values().forEach(TopicSyncWorker::stop);
    }
}
