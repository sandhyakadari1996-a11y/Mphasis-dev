package com.fde.transaction.service;

import com.fde.transaction.enums.TransactionStatus;
import com.fde.transaction.model.AccountBalance;
import com.fde.transaction.model.TransactionRequest;
import com.fde.transaction.repository.AccountBalanceRepository;
import com.fde.transaction.repository.TransactionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retry tests: verify that transient failures are retried without
 * creating duplicate balance effects. Uses ACC-100 data from sample_data.sql.
 */
@SpringBootTest
@ActiveProfiles("test")
class RetryTest {

    @Autowired
    private TransactionRequestRepository txnRepo;

    @Autowired
    private AccountBalanceRepository balanceRepo;

    @Autowired
    private BackgroundJobService backgroundJob;

    @Autowired
    private TransientFailureSimulator simulator;

    @Autowired
    private TransactionProcessorService processor;

    @BeforeEach
    void setUp() {
        simulator.reset();
        // Do NOT delete — use existing sample data from DB
    }

    @Test
    @DisplayName("TXN-0001: Simulated failure on first attempt, succeed on retry")
    void retryShouldSucceedOnSecondAttempt() {
        // Register TXN-0001 to fail on first processing attempt
        simulator.registerFailure("TXN-0001");

        // Reset TXN-0001 to RECEIVED so we can re-process
        TransactionRequest txn = txnRepo.findByTransactionId("TXN-0001")
                .orElseThrow(() -> new RuntimeException("TXN-0001 not found"));
        txn.setStatus(TransactionStatus.RECEIVED);
        txn.setErrorMessage(null);
        txn.setRetryCount(0);
        txnRepo.save(txn);

        // Reset balance to known value
        balanceRepo.deleteById("ACC-100");

        // First run: fails → RETRY_PENDING (simulator fails on attempt #1)
        backgroundJob.runOnce();
        TransactionRequest afterFirst = txnRepo.findByTransactionId("TXN-0001").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("After 1st run: status=" + afterFirst.getStatus() + " retries=" + afterFirst.getRetryCount());
        assertThat(afterFirst.getStatus()).isEqualTo(TransactionStatus.RETRY_PENDING);
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);

        // Second run: simulator succeeds now (only fails once) → PROCESSED
        backgroundJob.runOnce();
        TransactionRequest afterSecond = txnRepo.findByTransactionId("TXN-0001").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("After 2nd run: status=" + afterSecond.getStatus() + " retries=" + afterSecond.getRetryCount());
        assertThat(afterSecond.getStatus()).isEqualTo(TransactionStatus.PROCESSED);

        // Balance should be 1000 (only credited ONCE, not twice)
        AccountBalance balance = balanceRepo.findById("ACC-100").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("Final balance: " + balance.getBalance());
        assertThat(balance.getBalance()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("TXN-0002: Retry should not cause duplicate balance update")
    void retryShouldNotDoubleBalanceUpdate() {
        simulator.registerFailure("TXN-0002");

        // Reset TXN-0002 to RECEIVED
        TransactionRequest txn = txnRepo.findByTransactionId("TXN-0002")
                .orElseThrow(() -> new RuntimeException("TXN-0002 not found"));
        txn.setStatus(TransactionStatus.RECEIVED);
        txn.setErrorMessage(null);
        txn.setRetryCount(0);
        txnRepo.save(txn);

        // Capture balance before (ACC-100 has 1300 from sample data)
        AccountBalance before = balanceRepo.findById("ACC-100").orElseThrow(() -> new RuntimeException("not found"));
        BigDecimal balanceBefore = before.getBalance();
        System.out.println("Balance before retry test: " + balanceBefore);

        // First run: fails → RETRY_PENDING (no balance change)
        backgroundJob.runOnce();

        // Second run: succeeds → balance decreases by 200 (DEBIT)
        backgroundJob.runOnce();

        AccountBalance after = balanceRepo.findById("ACC-100").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("Balance after retry test: " + after.getBalance());
        assertThat(after.getBalance()).isEqualTo(balanceBefore.subtract(new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("TXN-0003: Transaction should reach FAILED after max retries exhausted")
    void retryExhaustionShouldMarkAsFailed() {
        simulator.registerFailure("TXN-0003");

        // Reset TXN-0003 with maxRetries=1 (fail after just 1 retry)
        TransactionRequest txn = txnRepo.findByTransactionId("TXN-0003")
                .orElseThrow(() -> new RuntimeException("TXN-0003 not found"));
        txn.setStatus(TransactionStatus.RECEIVED);
        txn.setErrorMessage(null);
        txn.setRetryCount(0);
        txn.setMaxRetries(1);
        txnRepo.save(txn);

        // First process: fails → retry_count=1 (= maxRetries) → RETRY_PENDING
        processor.process(txn.getId());
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.RETRY_PENDING);
        assertThat(txn.getRetryCount()).isEqualTo(1);
        System.out.println("After 1st failure: status=" + txn.getStatus() + " retries=" + txn.getRetryCount());

        // Manually trigger one more failure — now retries exhausted → FAILED
        txn.setStatus(TransactionStatus.PROCESSING);
        txnRepo.save(txn);
        processor.handleTransientError(txn, "Retry exhausted", new RuntimeException("test"));

        TransactionRequest failed = txnRepo.findByTransactionId("TXN-0003").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("Final status: " + failed.getStatus());
        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }
}
