package com.fde.transaction.service;

import com.fde.transaction.dto.ProcessingSummaryDTO;
import com.fde.transaction.enums.TransactionStatus;
import com.fde.transaction.enums.TransactionType;
import com.fde.transaction.model.AccountBalance;
import com.fde.transaction.model.ProcessingLog;
import com.fde.transaction.model.TransactionRequest;
import com.fde.transaction.repository.AccountBalanceRepository;
import com.fde.transaction.repository.ProcessingLogRepository;
import com.fde.transaction.repository.TransactionRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import javax.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Core transaction processing engine.
 *
 * Responsibilities:
 * - Idempotent processing (DB unique constraints + app-level check)
 * - Sequence-based ordering: a request with seq N waits for seq 1..N-1 to resolve
 * - Balance updates with optimistic locking
 * - Retry-safe failure handling
 * - Processing audit log
 */
@Slf4j
@Service
public class TransactionProcessorService {

    private final TransactionRequestRepository transactionRepo;
    private final AccountBalanceRepository balanceRepo;
    private final ProcessingLogRepository logRepo;
    private final TransientFailureSimulator failureSimulator;

    public TransactionProcessorService(TransactionRequestRepository transactionRepo,
                                       AccountBalanceRepository balanceRepo,
                                       ProcessingLogRepository logRepo,
                                       TransientFailureSimulator failureSimulator) {
        this.transactionRepo = transactionRepo;
        this.balanceRepo = balanceRepo;
        this.logRepo = logRepo;
        this.failureSimulator = failureSimulator;
    }

    /** Terminal statuses — a transaction that has reached a final state. */
    private static final List<TransactionStatus> TERMINAL_STATUSES =
            Arrays.asList(TransactionStatus.PROCESSED, TransactionStatus.DUPLICATE,
                    TransactionStatus.FAILED);

    /**
     * Process a single transaction request by its ID.
     * This is the main entry point called by the background scheduler.
     */
    @Transactional
    public void process(Long transactionId) {
        TransactionRequest txn = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));

        // Skip if already in a terminal state
        if (TERMINAL_STATUSES.contains(txn.getStatus())) {
            log.debug("Skipping {} ({}) — already in terminal status {}",
                    txn.getTransactionId(), txn.getRequestId(), txn.getStatus());
            return;
        }

        // Skip if currently being processed (concurrency guard)
        if (txn.getStatus() == TransactionStatus.PROCESSING) {
            log.debug("Skipping {} — already in PROCESSING state", txn.getTransactionId());
            return;
        }

        // Check sequence ordering: all predecessors must be resolved
        if (txn.getSequenceNumber() != null) {
            List<TransactionRequest> unresolved = transactionRepo.findUnresolvedPredecessors(
                    txn.getAccountId(), txn.getSequenceNumber(),
                    TERMINAL_STATUSES);
            if (!unresolved.isEmpty()) {
                log.info("Transaction {} (seq={}) waiting for {} unresolved predecessors",
                        txn.getTransactionId(), txn.getSequenceNumber(), unresolved.size());
                txn.setStatus(TransactionStatus.PENDING);
                transactionRepo.save(txn);
                return;
            }
        }

        // Move to PROCESSING
        txn.setStatus(TransactionStatus.PROCESSING);
        transactionRepo.save(txn);

        try {
            // Simulate transient failure
            if (failureSimulator.shouldFail(txn.getTransactionId())) {
                throw new RuntimeException("Simulated transient failure for " + txn.getTransactionId());
            }

            // Apply the transaction to the account balance
            applyTransaction(txn);

            // Mark as PROCESSED
            txn.setStatus(TransactionStatus.PROCESSED);
            transactionRepo.save(txn);
            auditLog(txn, "PROCESSED", "Balance updated successfully");

            log.info("Processed {} ({}) type={} amount={} -> balance updated",
                    txn.getTransactionId(), txn.getRequestId(),
                    txn.getTransactionType(), txn.getAmount());

        } catch (OptimisticLockException e) {
            // Concurrent modification — mark for retry
            handleTransientError(txn, "Optimistic lock conflict", e);
        } catch (RuntimeException e) {
            handleTransientError(txn, e.getMessage(), e);
        }
    }

    /**
     * Mark a transaction for retry or permanent failure.
     */
    @Transactional
    public void handleTransientError(TransactionRequest txn, String message, Throwable cause) {
        txn.setErrorMessage(message);

        if (txn.getRetryCount() < txn.getMaxRetries()) {
            txn.setRetryCount(txn.getRetryCount() + 1);
            txn.setStatus(TransactionStatus.RETRY_PENDING);
            transactionRepo.save(txn);
            auditLog(txn, "RETRY_PENDING",
                    message + " (retry " + txn.getRetryCount() + "/" + txn.getMaxRetries() + ")");
            log.warn("Transaction {} marked for retry ({}/{}) — {}",
                    txn.getTransactionId(), txn.getRetryCount(), txn.getMaxRetries(), message);
        } else {
            txn.setStatus(TransactionStatus.FAILED);
            transactionRepo.save(txn);
            auditLog(txn, "FAILED", message + " (retry exhausted)");
            log.error("Transaction {} permanently failed after {} retries: {}",
                    txn.getTransactionId(), txn.getMaxRetries(), message);
        }
    }

    /**
     * Detect and mark a duplicate request. Called before inserting a new transaction.
     */
    @Transactional
    public void markDuplicate(TransactionRequest original, String duplicateRequestId) {
        auditLog(original, "DUPLICATE_DETECTED",
                "Duplicate request_id=" + duplicateRequestId + " for txnId=" + original.getTransactionId());
        log.warn("Duplicate detected: request_id={} is duplicate of transactionId={}",
                duplicateRequestId, original.getTransactionId());
    }

    /**
     * Apply a transaction to the account balance.
     * CREDIT and REVERSAL add to balance; DEBIT subtracts.
     */
    private void applyTransaction(TransactionRequest txn) {
        AccountBalance balance = balanceRepo.findById(txn.getAccountId())
                .orElseGet(() -> {
                    AccountBalance newBalance = new AccountBalance(txn.getAccountId(), txn.getCurrency());
                    return balanceRepo.save(newBalance);
                });

        BigDecimal currentBalance = balance.getBalance();
        BigDecimal newBalance;

        switch (txn.getTransactionType()) {
            case CREDIT:
                newBalance = currentBalance.add(txn.getAmount());
                break;
            case DEBIT:
                newBalance = currentBalance.subtract(txn.getAmount());
                break;
            case REVERSAL:
                newBalance = currentBalance.add(txn.getAmount());
                break;
            default:
                throw new IllegalArgumentException("Unknown transaction type: " + txn.getTransactionType());
        }

        balance.setBalance(newBalance);
        balanceRepo.save(balance);

        log.info("Account {} balance: {} -> {} (type={})",
                txn.getAccountId(), currentBalance, newBalance, txn.getTransactionType());
    }

    /**
     * Write an audit log entry.
     */
    private void auditLog(TransactionRequest txn, String action, String result) {
        ProcessingLog entry = new ProcessingLog();
        entry.setTransactionId(txn.getTransactionId());
        entry.setRequestId(txn.getRequestId());
        entry.setAction(action);
        entry.setResult(result);
        logRepo.save(entry);
    }

    /**
     * Generate a processing summary with counts by status.
     */
    public ProcessingSummaryDTO generateSummary() {
        ProcessingSummaryDTO summary = new ProcessingSummaryDTO();

        for (TransactionStatus status : TransactionStatus.values()) {
            long count = transactionRepo.countByStatus(status);
            if (count > 0) {
                summary.incrementStatus(status.name());
            }
        }

        summary.setTotalSubmitted(transactionRepo.count());
        return summary;
    }
}
