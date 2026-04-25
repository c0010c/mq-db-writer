package com.example.cdcsync.sync;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

public class ConsumeExecutionTemplate {

    private final TransactionTemplate transactionTemplate;

    public ConsumeExecutionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ConsumeResult execute(TopicBatchContext batchContext,
                                 List<ConsumedMessage> messages,
                                 ConsumeOrderlyContext orderlyContext,
                                 TopicMessageHandler messageHandler,
                                 long suspendCurrentQueueTimeMillis) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (int index = 0; index < messages.size(); index++) {
                    messageHandler.handle(batchContext, messages.get(index), index);
                }
            });
            return ConsumeResult.SUCCESS;
        } catch (Exception ex) {
            orderlyContext.setSuspendCurrentQueueTimeMillis(suspendCurrentQueueTimeMillis);
            return ConsumeResult.SUSPEND_CURRENT_QUEUE_A_MOMENT;
        }
    }
}
