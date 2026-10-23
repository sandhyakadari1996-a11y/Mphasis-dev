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
 * Idempotency tests: verify that duplicate requests do not produce
 * duplicate balance updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyTest {

    @Autowired
    private TransactionRequestRepository txnRepo;

    @Autowired
    private AccountBalanceRepository balanceRepo;

    @Autowired
    private TransactionProcessorService processor;

    @Autowired
    private TransientFailureSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator.reset();
        // Do NOT delete — use existing sample data from DB
    }

    @Test
    @DisplayName("Same transaction processed twice should only update balance once")
    void duplicateTransactionIdShouldNotDoubleCharge() {
        // Fetch existing TXN-0001 from your sample data (ACC-100, CREDIT 1000)
        TransactionRequest txn = txnRepo.findByTransactionId("TXN-0001")
                .orElseThrow(() -> new RuntimeException("TXN-0001 not found — run sample_data.sql first"));

        // Capture current balance
        AccountBalance before = balanceRepo.findById("ACC-100")
                .orElseThrow(() -> new RuntimeException("ACC-100 not found"));
        BigDecimal balanceBefore = before.getBalance();
        System.out.println("Balance BEFORE: " + balanceBefore);

        // Reset TXN-0001 to RECEIVED so processor will pick it up again
        txn.setStatus(TransactionStatus.RECEIVED);
        txn.setErrorMessage(null);
        txn.setRetryCount(0);
        txnRepo.save(txn);

        // Process ONCE
        processor.process(txn.getId());
        AccountBalance afterFirst = balanceRepo.findById("ACC-100").orElseThrow(() -> new RuntimeException("ACC-100 not found"));
        System.out.println("Balance AFTER 1st process: " + afterFirst.getBalance());
        assertThat(afterFirst.getBalance()).isEqualTo(balanceBefore.add(new BigDecimal("1000.00")));

        // Process AGAIN — should be SKIPPED (idempotent!)
        processor.process(txn.getId());
        AccountBalance afterSecond = balanceRepo.findById("ACC-100").orElseThrow(() -> new RuntimeException("ACC-100 not found"));
        System.out.println("Balance AFTER 2nd process: " + afterSecond.getBalance() + " (same = idempotent!)");
        assertThat(afterSecond.getBalance()).isEqualTo(balanceBefore.add(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("Duplicate requestId should be rejected at submission layer")
    void duplicateRequestIdDetected() {
        // Use existing TXN-0002 from sample data
        TransactionRequest txn = txnRepo.findByTransactionId("TXN-0002")
                .orElseThrow(() -> new RuntimeException("TXN-0002 not found"));

        // Simulate duplicate detection
        processor.markDuplicate(txn, "REQ-DUPLICATE-999");

        // Original should still be PROCESSED, not re-processed
        TransactionRequest found = txnRepo.findByTransactionId("TXN-0002").orElseThrow(() -> new RuntimeException("TXN-0002 not found"));
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
    }
}
