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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Out-of-order processing tests using sample_data.sql (ACC-200 scenario).
 * ACC-200 has seq-3 inserted BEFORE seq-1 and seq-2.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutOfOrderTest {

    @Autowired
    private TransactionRequestRepository txnRepo;

    @Autowired
    private AccountBalanceRepository balanceRepo;

    @Autowired
    private BackgroundJobService backgroundJob;

    @Autowired
    private TransientFailureSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator.reset();
        // Do NOT delete — use existing sample data from DB
    }

    @Test
    @DisplayName("ACC-200: Seq-3 arrives before seq-1 and seq-2 — all should process in correct order")
    void outOfOrderShouldWaitForPredecessor() {
        // Use existing ACC-200 data from sample_data.sql:
        // TXN-0004 (seq=3, CREDIT 5000) — inserted first (out of order!)
        // TXN-0005 (seq=1, CREDIT 2000)
        // TXN-0006 (seq=2, DEBIT 300)
        // Expected balance = 2000 - 300 + 5000 = 6700

        // Reset all ACC-200 transactions to RECEIVED so we can re-process
        txnRepo.findByAccountId("ACC-200").forEach(txn -> {
            txn.setStatus(TransactionStatus.RECEIVED);
            txn.setErrorMessage(null);
            txn.setRetryCount(0);
        });
        txnRepo.saveAll(txnRepo.findByAccountId("ACC-200"));

        // Delete existing balance so we start fresh
        balanceRepo.deleteById("ACC-200");

        System.out.println("=== Before processing ===");
        txnRepo.findByAccountId("ACC-200").forEach(txn ->
            System.out.println("  " + txn.getTransactionId() + " seq=" + txn.getSequenceNumber() + " status=" + txn.getStatus()));

        // Run background job — processes in sequence order: 1 → 2 → 3
        backgroundJob.runOnce();

        System.out.println("=== After processing ===");
        txnRepo.findByAccountId("ACC-200").forEach(txn ->
            System.out.println("  " + txn.getTransactionId() + " seq=" + txn.getSequenceNumber() + " status=" + txn.getStatus()));

        // All three should now be PROCESSED
        assertThat(txnRepo.findByTransactionId("TXN-0005").orElseThrow(() -> new RuntimeException("not found")).getStatus())
                .isEqualTo(TransactionStatus.PROCESSED);  // seq-1
        assertThat(txnRepo.findByTransactionId("TXN-0006").orElseThrow(() -> new RuntimeException("not found")).getStatus())
                .isEqualTo(TransactionStatus.PROCESSED);  // seq-2
        assertThat(txnRepo.findByTransactionId("TXN-0004").orElseThrow(() -> new RuntimeException("not found")).getStatus())
                .isEqualTo(TransactionStatus.PROCESSED);  // seq-3

        // Verify final balance = 2000 - 300 + 5000 = 6700
        AccountBalance balance = balanceRepo.findById("ACC-200").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("Final balance for ACC-200: " + balance.getBalance());
        assertThat(balance.getBalance()).isEqualTo(new BigDecimal("6700.00"));
    }

    @Test
    @DisplayName("ACC-300: Seq-1,2,3 in order — should all process normally")
    void inOrderShouldProcessNormally() {
        // Use existing ACC-300 data from sample_data.sql (in order):
        // TXN-0007 (seq=1, CREDIT 10000 EUR)
        // TXN-0008 (seq=2, DEBIT 1500 EUR)
        // TXN-0009 (seq=3, REVERSAL 500 EUR)
        // Expected balance = 10000 - 1500 + 500 = 9000

        // Reset all ACC-300 transactions to RECEIVED
        List<TransactionRequest> acc300Txns = txnRepo.findByAccountId("ACC-300");
        acc300Txns.forEach(txn -> {
            txn.setStatus(TransactionStatus.RECEIVED);
            txn.setErrorMessage(null);
            txn.setRetryCount(0);
        });
        txnRepo.saveAll(acc300Txns);

        // Delete existing balance so we start fresh
        balanceRepo.deleteById("ACC-300");

        backgroundJob.runOnce();

        AccountBalance balance = balanceRepo.findById("ACC-300").orElseThrow(() -> new RuntimeException("not found"));
        System.out.println("Final balance for ACC-300: " + balance.getBalance());
        assertThat(balance.getBalance()).isEqualTo(new BigDecimal("9000.00"));
    }
}
