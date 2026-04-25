package com.example.cdcsync.sync;

import com.example.cdcsync.config.model.TopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class TopicSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(TopicSyncWorker.class);

    private TopicSyncWorkerConfig config;
    private final OrderedConsumerFactory consumerFactory;
    private final ConsumeExecutionTemplate consumeExecutionTemplate;
    private final TopicMessageHandler messageHandler;
    private GracefulShutdownCoordinator shutdownCoordinator;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile OrderedConsumer consumer;

    public TopicSyncWorker(TopicSyncWorkerConfig config,
                           OrderedConsumerFactory consumerFactory,
                           ConsumeExecutionTemplate consumeExecutionTemplate,
                           TopicMessageHandler messageHandler,
                           GracefulShutdownCoordinator shutdownCoordinator) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory must not be null");
        this.consumeExecutionTemplate = Objects.requireNonNull(consumeExecutionTemplate, "consumeExecutionTemplate must not be null");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        this.shutdownCoordinator = Objects.requireNonNull(shutdownCoordinator, "shutdownCoordinator must not be null");
    }

    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }

        TopicProperties topic = config.topic();
        TopicConsumerConfig consumerConfig = new TopicConsumerConfig(
                topic.getTopic(),
                config.consumerGroup(),
                topic.getBatchSize(),
                topic.getConsumeThreadMin(),
                topic.getConsumeThreadMax()
        );

        OrderedConsumer createdConsumer = consumerFactory.create(consumerConfig, this::consumeOrdered);
        createdConsumer.start();

        this.consumer = createdConsumer;
        started.set(true);
    }

    public synchronized boolean stop() {
        if (!started.get()) {
            return true;
        }

        shutdownCoordinator.requestShutdown();
        boolean drained = waitInflightBatches();

        OrderedConsumer activeConsumer = this.consumer;
        if (activeConsumer != null) {
            activeConsumer.shutdown();
        }

        started.set(false);
        return drained;
    }

    public synchronized void rebuild(TopicSyncWorkerConfig newConfig) throws Exception {
        stop();
        this.config = newConfig;
        this.shutdownCoordinator = new GracefulShutdownCoordinator();
        start();
    }

    public boolean isStarted() {
        return started.get();
    }

    private ConsumeResult consumeOrdered(List<ConsumedMessage> messages, ConsumeOrderlyContext context) {
        if (!shutdownCoordinator.tryEnterBatch()) {
            context.setSuspendCurrentQueueTimeMillis(config.suspendCurrentQueueTimeMillis());
            return ConsumeResult.SUSPEND_CURRENT_QUEUE_A_MOMENT;
        }

        try {
            TopicProperties topic = config.topic();
            TopicBatchContext batchContext = new TopicBatchContext(
                    topic.getTopic(),
                    topic.getDatasource(),
                    topic.getTargetTable(),
                    topic.getPrimaryKey(),
                    config.consumerGroup(),
                    messages.size()
            );
            return consumeExecutionTemplate.execute(
                    batchContext,
                    messages,
                    context,
                    messageHandler,
                    config.suspendCurrentQueueTimeMillis()
            );
        } finally {
            shutdownCoordinator.batchCompleted();
        }
    }

    private boolean waitInflightBatches() {
        try {
            return shutdownCoordinator.awaitInflightBatches(config.gracefulShutdownTimeout());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("interrupted while waiting in-flight batch finish, topic={}", config.topic().getTopic());
            return false;
        }
    }
}
