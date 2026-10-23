package com.fde.transaction.rest;

import com.fde.transaction.dto.ProcessingSummaryDTO;
import com.fde.transaction.dto.TransactionRequestDTO;
import com.fde.transaction.enums.TransactionStatus;
import com.fde.transaction.exception.DuplicateTransactionException;
import com.fde.transaction.exception.TransactionValidationException;
import com.fde.transaction.model.TransactionRequest;
import com.fde.transaction.repository.TransactionRequestRepository;
import com.fde.transaction.service.BackgroundJobService;
import com.fde.transaction.service.TransactionProcessorService;
import com.fde.transaction.service.TransactionValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for submitting transactions and triggering processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRequestRepository repo;
    private final TransactionValidationService validationService;
    private final TransactionProcessorService processorService;
    private final BackgroundJobService backgroundJob;

    public TransactionController(TransactionRequestRepository repo,
                                 TransactionValidationService validationService,
                                 TransactionProcessorService processorService,
                                 BackgroundJobService backgroundJob) {
        this.repo = repo;
        this.validationService = validationService;
        this.processorService = processorService;
        this.backgroundJob = backgroundJob;
    }

    /**
     * Submit a single transaction request.
     */
    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody TransactionRequestDTO dto) {
        // Validate
        List<String> violations = validationService.validate(dto);
        if (!violations.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("valid", false);
            body.put("violations", violations);
            body.put("status", TransactionStatus.FAILED.name());
            return ResponseEntity.badRequest().body(body);
        }

        // Check for duplicates by requestId
        repo.findByRequestId(dto.getRequestId()).ifPresent(existing -> {
            if (existing.getStatus() != TransactionStatus.FAILED) {
                processorService.markDuplicate(existing, dto.getRequestId());
                throw new DuplicateTransactionException(
                        "Duplicate requestId: " + dto.getRequestId() +
                        " (original transactionId: " + existing.getTransactionId() + ")");
            }
        });

        // Check for duplicates by transactionId
        repo.findByTransactionId(dto.getTransactionId()).ifPresent(existing -> {
            if (existing.getStatus() != TransactionStatus.FAILED) {
                processorService.markDuplicate(existing, dto.getRequestId());
                throw new DuplicateTransactionException(
                        "Duplicate transactionId: " + dto.getTransactionId());
            }
        });

        // Persist as RECEIVED
        TransactionRequest txn = new TransactionRequest();
        txn.setTransactionId(dto.getTransactionId());
        txn.setRequestId(dto.getRequestId());
        txn.setAccountId(dto.getAccountId());
        txn.setTransactionType(dto.getTransactionType());
        txn.setAmount(dto.getAmount());
        txn.setCurrency(dto.getCurrency());
        txn.setSequenceNumber(dto.getSequenceNumber());
        txn.setEventTime(dto.getEventTime() != null ? dto.getEventTime() : java.time.LocalDateTime.now());
        txn.setStatus(TransactionStatus.RECEIVED);
        txn = repo.save(txn);

        log.info("Received transaction: {} (req={}) for account {}",
                txn.getTransactionId(), txn.getRequestId(), txn.getAccountId());

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("transactionId", txn.getTransactionId());
        response.put("requestId", txn.getRequestId());
        response.put("status", txn.getStatus().name());
        return ResponseEntity.ok(response);
    }

    /**
     * Submit a batch of transaction requests.
     */
    @PostMapping("/batch")
    public ResponseEntity<?> submitBatch(@RequestBody List<TransactionRequestDTO> dtos) {
        Map<String, Object> results = new HashMap<>();
        int accepted = 0, rejected = 0, duplicated = 0;

        for (TransactionRequestDTO dto : dtos) {
            try {
                ResponseEntity<?> resp = submit(dto);
                if (resp.getStatusCodeValue() == 200) {
                    accepted++;
                }
            } catch (DuplicateTransactionException e) {
                duplicated++;
                log.warn("Duplicate in batch: {}", e.getMessage());
            } catch (Exception e) {
                rejected++;
                log.error("Rejected in batch: {}", e.getMessage());
            }
        }

        results.put("total", dtos.size());
        results.put("accepted", accepted);
        results.put("rejected", rejected);
        results.put("duplicated", duplicated);

        return ResponseEntity.ok(results);
    }

    /**
     * Trigger a synchronous processing run.
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingSummaryDTO> triggerProcessing() {
        log.info("Manual processing trigger received");
        backgroundJob.runOnce();
        return ResponseEntity.ok(processorService.generateSummary());
    }

    /**
     * Get processing summary.
     */
    @GetMapping("/summary")
    public ResponseEntity<ProcessingSummaryDTO> getSummary() {
        return ResponseEntity.ok(processorService.generateSummary());
    }

    /**
     * Get all transactions.
     */
    @GetMapping
    public ResponseEntity<List<TransactionRequest>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Get a single transaction by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
