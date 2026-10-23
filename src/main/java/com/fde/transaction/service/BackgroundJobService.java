package com.fde.transaction.service;

import com.fde.transaction.enums.TransactionStatus;
import com.fde.transaction.model.TransactionRequest;
import com.fde.transaction.repository.TransactionRequestRepository;
import com.fde.transaction.service.TransactionProcessorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Background job that periodically picks up RECEIVED and RETRY_PENDING transactions
 * and feeds them to the processor.
 *
 * In production this would be replaced by a queue consumer (Kafka, SQS, RabbitMQ).
 * The adapter pattern used here makes that swap straightforward.
 */
@Slf4j
@Service
public class BackgroundJobService {

    private final TransactionRequestRepository repo;
    private final TransactionProcessorService processor;

    public BackgroundJobService(TransactionRequestRepository repo,
                                TransactionProcessorService processor) {
        this.repo = repo;
        this.processor = processor;
    }

    /**
     * Runs every 2 seconds to pick up new and retryable transactions.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1000)
    public void run() {
        List<TransactionRequest> received = repo.findByStatusOrderBySequenceNumberAsc(TransactionStatus.RECEIVED);
        List<TransactionRequest> retryPending = repo.findByStatusOrderBySequenceNumberAsc(TransactionStatus.RETRY_PENDING);

        int total = received.size() + retryPending.size();
        if (total == 0) {
            return;
        }

        log.info("Background job: found {} transactions to process ({} RECEIVED, {} RETRY_PENDING)",
                total, received.size(), retryPending.size());

        received.forEach(txn -> processor.process(txn.getId()));
        retryPending.forEach(txn -> processor.process(txn.getId()));
    }

    /**
     * Synchronous one-shot run — useful for tests and manual triggers.
     * Keeps processing until no more work is found (handles PENDING -> PROCESSED cascades).
     */
    public void runOnce() {
        int maxIterations = 20; // Prevent infinite loops
        for (int i = 0; i < maxIterations; i++) {
            List<TransactionRequest> received = repo.findByStatusOrderBySequenceNumberAsc(TransactionStatus.RECEIVED);
            List<TransactionRequest> retryPending = repo.findByStatusOrderBySequenceNumberAsc(TransactionStatus.RETRY_PENDING);

            if (received.isEmpty() && retryPending.isEmpty()) {
                break;
            }

            received.forEach(txn -> processor.process(txn.getId()));
            retryPending.forEach(txn -> processor.process(txn.getId()));
        }
    }
}
