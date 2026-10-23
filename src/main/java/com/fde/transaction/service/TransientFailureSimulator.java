package com.fde.transaction.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates transient failures for specific transactions.
 *
 * Strategy: A transaction configured for simulation will fail on its first N
 * processing attempts and succeed on the (N+1)th. This allows deterministic
 * testing of the retry mechanism without random flakiness.
 *
 * Configuration is via the Set of transactionIds to simulate.
 */
@Component
public class TransientFailureSimulator {

    /** Transaction IDs that should fail once before succeeding. */
    private final Set<String> simulateFailureFor = ConcurrentHashMap.newKeySet();

    /** Tracks how many times each transactionId has been attempted. */
    private final ConcurrentHashMap<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();

    /**
     * Register a transactionId to simulate a transient failure on its first attempt.
     */
    public void registerFailure(String transactionId) {
        simulateFailureFor.add(transactionId);
        attemptCounts.putIfAbsent(transactionId, new AtomicInteger(0));
    }

    /**
     * Check if the next attempt for this transactionId should fail.
     * Returns true on the first attempt, false thereafter (succeeds on retry).
     */
    public boolean shouldFail(String transactionId) {
        if (!simulateFailureFor.contains(transactionId)) {
            return false;
        }

        AtomicInteger counter = attemptCounts.computeIfAbsent(transactionId, k -> new AtomicInteger(0));
        return counter.getAndIncrement() == 0;
    }

    /**
     * Reset all counters — useful for testing.
     */
    public void reset() {
        simulateFailureFor.clear();
        attemptCounts.clear();
    }
}
