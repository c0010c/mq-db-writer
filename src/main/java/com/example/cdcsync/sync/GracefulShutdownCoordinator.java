package com.example.cdcsync.sync;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class GracefulShutdownCoordinator {

    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicInteger inFlightBatches = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition zeroInflight = lock.newCondition();

    public boolean tryEnterBatch() {
        if (shutdownRequested.get()) {
            return false;
        }

        inFlightBatches.incrementAndGet();
        if (shutdownRequested.get()) {
            batchCompleted();
            return false;
        }
        return true;
    }

    public void batchCompleted() {
        int current = inFlightBatches.decrementAndGet();
        if (current <= 0) {
            lock.lock();
            try {
                zeroInflight.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public void requestShutdown() {
        shutdownRequested.set(true);
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    public int inFlightBatchCount() {
        return inFlightBatches.get();
    }

    public boolean awaitInflightBatches(Duration timeout) throws InterruptedException {
        long nanos = timeout.toNanos();
        lock.lock();
        try {
            while (inFlightBatches.get() > 0) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = zeroInflight.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean awaitInflightBatches(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return awaitInflightBatches(Duration.ofNanos(timeUnit.toNanos(timeout)));
    }
}
