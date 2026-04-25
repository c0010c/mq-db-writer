package com.example.cdcsync.sync;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GracefulShutdownCoordinatorTest {

    @Test
    void shouldRejectNewBatchAfterShutdownRequested() {
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator();

        coordinator.requestShutdown();

        assertThat(coordinator.tryEnterBatch()).isFalse();
    }

    @Test
    void shouldWaitUntilInflightBatchCompleted() throws Exception {
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            assertThat(coordinator.tryEnterBatch()).isTrue();
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            coordinator.batchCompleted();
        });
        thread.start();

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        coordinator.requestShutdown();

        assertThat(coordinator.awaitInflightBatches(50, TimeUnit.MILLISECONDS)).isFalse();

        release.countDown();

        assertThat(coordinator.awaitInflightBatches(1, TimeUnit.SECONDS)).isTrue();
        thread.join(1000);
    }
}
