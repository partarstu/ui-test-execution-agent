package org.tarik.ta.error;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RetryState {
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);

    public void reset() {
        attempts.set(0);
        startTime.set(0);
    }

    public int incrementAttempts() {
        return attempts.incrementAndGet();
    }

    public void startIfNotStarted() {
        startTime.compareAndSet(0, System.currentTimeMillis());
    }

    public long getElapsedTime() {
        long start = startTime.get();
        return start == 0 ? 0 : System.currentTimeMillis() - start;
    }

    public int getAttempts() {
        return attempts.get();
    }
}
