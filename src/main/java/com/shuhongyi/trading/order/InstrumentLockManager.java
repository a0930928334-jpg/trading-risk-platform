package com.shuhongyi.trading.order;

import com.shuhongyi.trading.order.exception.InstrumentBusyException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock per instrument: every command that changes an instrument's orders (place, cancel, match)
 * runs while holding it, so each order book is changed by one thread at a time.
 * Different instruments proceed in parallel. The locks live in this JVM, which is why v0.1 runs as a single instance.
 */
@Component
public class InstrumentLockManager {

    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(Long instrumentId, Supplier<T> action) {
        // Fair locks hand the instrument to waiting requests in arrival order.
        ReentrantLock lock = locks.computeIfAbsent(instrumentId, id -> new ReentrantLock(true));
        boolean acquired;
        try {
            acquired = lock.tryLock(ACQUIRE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InstrumentBusyException(instrumentId);
        }
        if (!acquired) {
            throw new InstrumentBusyException(instrumentId);
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
