package com.shuhongyi.trading.order;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentLockManagerTest {

    private final InstrumentLockManager lockManager = new InstrumentLockManager();

    @Test
    void commandsOnTheSameInstrumentNeverOverlap() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 200; i++) {
                futures.add(executor.submit(() -> lockManager.withLock(1L, () -> {
                    int now = inside.incrementAndGet();
                    maxInside.accumulateAndGet(now, Math::max);
                    Thread.onSpinWait();
                    inside.decrementAndGet();
                    return null;
                })));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(maxInside.get()).isEqualTo(1);
    }

    @Test
    void differentInstrumentsRunInParallel() throws Exception {
        CountDownLatch bothInside = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> lockManager.withLock(1L, () -> awaitQuietly(bothInside)));
            Future<Boolean> second = executor.submit(() -> lockManager.withLock(2L, () -> awaitQuietly(bothInside)));

            // Each holder waits for the other to be inside its own lock: this only completes if they overlap.
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lockIsReleasedWhenTheActionThrows() {
        try {
            lockManager.withLock(1L, () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // ignored
        }

        assertThat(lockManager.withLock(1L, () -> "acquired again")).isEqualTo("acquired again");
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
