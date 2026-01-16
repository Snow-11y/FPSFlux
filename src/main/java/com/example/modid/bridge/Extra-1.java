package com.example.modid.bridge;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker pattern implementation to prevent cascading failures.
 */
public final class CircuitBreaker {
    
    public enum State { CLOSED, OPEN, HALF_OPEN }
    
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final String name;
    
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger successCount = new AtomicInteger(0);

    public CircuitBreaker(int failureThreshold, long resetTimeoutMs, String name) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.name = name;
    }

    public boolean allowRequest() {
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    yield true;
                }
                yield false;
            }
            case HALF_OPEN -> true;
        };
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= 3) {
                reset();
            }
        }
        failureCount.set(0);
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        successCount.set(0);
        
        if (failureCount.incrementAndGet() >= failureThreshold) {
            state = State.OPEN;
        }
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
    }
}
