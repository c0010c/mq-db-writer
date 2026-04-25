package com.example.cdcsync.sync;

import com.example.cdcsync.config.model.TopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TopicSyncWorkerTest {

    @Test
    void shouldCommitBatchWhenAllMessagesHandledSuccessfully() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        CapturingConsumerFactory consumerFactory = new CapturingConsumerFactory();

        List<String> handledMessageIds = new CopyOnWriteArrayList<>();
        TopicSyncWorker worker = new TopicSyncWorker(
                workerConfig("user_cdc_topic"),
                consumerFactory,
                new ConsumeExecutionTemplate(transactionManager),
                (batchContext, message, index) -> handledMessageIds.add(message.messageId()),
                new GracefulShutdownCoordinator()
        );

        worker.start();
        ConsumeOrderlyContext orderlyContext = new ConsumeOrderlyContext();
        ConsumeResult result = consumerFactory.consumer.listener.consumeMessage(List.of(
                new ConsumedMessage("m1", "k1", 1, 10, "a".getBytes(StandardCharsets.UTF_8)),
                new ConsumedMessage("m2", "k2", 1, 11, "b".getBytes(StandardCharsets.UTF_8))
        ), orderlyContext);

        assertThat(result).isEqualTo(ConsumeResult.SUCCESS);
        assertThat(handledMessageIds).containsExactly("m1", "m2");
        assertThat(transactionManager.beginCount).isEqualTo(1);
        assertThat(transactionManager.commitCount).isEqualTo(1);
        assertThat(transactionManager.rollbackCount).isZero();
    }

    @Test
    void shouldRollbackAndSuspendWhenAnyMessageHandlerFails() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        CapturingConsumerFactory consumerFactory = new CapturingConsumerFactory();

        TopicSyncWorker worker = new TopicSyncWorker(
                workerConfig("device_cdc_topic"),
                consumerFactory,
                new ConsumeExecutionTemplate(transactionManager),
                (batchContext, message, index) -> {
                    if (index == 1) {
                        throw new IllegalStateException("boom");
                    }
                },
                new GracefulShutdownCoordinator()
        );

        worker.start();
        ConsumeOrderlyContext orderlyContext = new ConsumeOrderlyContext();
        ConsumeResult result = consumerFactory.consumer.listener.consumeMessage(List.of(
                new ConsumedMessage("m1", "k1", 1, 10, null),
                new ConsumedMessage("m2", "k2", 1, 11, null)
        ), orderlyContext);

        assertThat(result).isEqualTo(ConsumeResult.SUSPEND_CURRENT_QUEUE_A_MOMENT);
        assertThat(orderlyContext.getSuspendCurrentQueueTimeMillis()).isEqualTo(5000L);
        assertThat(transactionManager.beginCount).isEqualTo(1);
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isEqualTo(1);
    }

    @Test
    void shouldShutdownConsumerWhenStopped() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        CapturingConsumerFactory consumerFactory = new CapturingConsumerFactory();

        TopicSyncWorker worker = new TopicSyncWorker(
                workerConfig("order_topic"),
                consumerFactory,
                new ConsumeExecutionTemplate(transactionManager),
                (batchContext, message, index) -> {
                },
                new GracefulShutdownCoordinator()
        );

        worker.start();
        boolean drained = worker.stop();

        assertThat(drained).isTrue();
        assertThat(consumerFactory.consumer.shutdownCalled).isTrue();
        assertThat(worker.isStarted()).isFalse();
    }

    private TopicSyncWorkerConfig workerConfig(String topicName) {
        TopicProperties topic = new TopicProperties();
        topic.setTopic(topicName);
        topic.setDatasource("syncDbA");
        topic.setTargetTable("user_info");
        topic.setPrimaryKey("id");
        topic.setBatchSize(10);
        topic.setConsumeThreadMin(1);
        topic.setConsumeThreadMax(1);

        return new TopicSyncWorkerConfig(topic, "group-" + topicName, 5000L, Duration.ofSeconds(2));
    }

    private static class CapturingConsumerFactory implements OrderedConsumerFactory {
        private CapturingConsumer consumer;

        @Override
        public OrderedConsumer create(TopicConsumerConfig config, OrderedMessageListener listener) {
            this.consumer = new CapturingConsumer(listener);
            return consumer;
        }
    }

    private static class CapturingConsumer implements OrderedConsumer {
        private final OrderedMessageListener listener;
        private boolean shutdownCalled;

        private CapturingConsumer(OrderedMessageListener listener) {
            this.listener = listener;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
            this.shutdownCalled = true;
        }
    }

    private static class RecordingTransactionManager implements PlatformTransactionManager {
        private int beginCount;
        private int commitCount;
        private int rollbackCount;
        private final List<TransactionStatus> statuses = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            beginCount++;
            SimpleTransactionStatus status = new SimpleTransactionStatus();
            statuses.add(status);
            return status;
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            commitCount++;
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            rollbackCount++;
        }
    }
}
