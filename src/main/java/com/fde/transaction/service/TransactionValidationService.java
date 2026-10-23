package com.fde.transaction.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fde.transaction.dto.TransactionRequestDTO;
import com.fde.transaction.exception.TransactionValidationException;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates incoming transaction requests before persistence.
 * Business rules:
 * - Amount must be positive for CREDIT and DEBIT.
 * - REVERSAL amount must be positive (it reverses a prior debit/credit).
 * - Currency must be a valid 3-letter code.
 * - AccountId must not be blank.
 */
@Slf4j
@Service
public class TransactionValidationService {

    /**
     * Validate a single DTO. Returns a list of violation messages;
     * empty list means the request is valid.
     */
    public List<String> validate(TransactionRequestDTO dto) {
        List<String> violations = new ArrayList<>();

        if (dto.getTransactionId() == null || dto.getTransactionId().trim().isEmpty()) {
            violations.add("transactionId is required");
        }
        if (dto.getRequestId() == null || dto.getRequestId().trim().isEmpty()) {
            violations.add("requestId is required");
        }
        if (dto.getAccountId() == null || dto.getAccountId().trim().isEmpty()) {
            violations.add("accountId is required");
        }
        if (dto.getTransactionType() == null) {
            violations.add("transactionType is required");
        }
        if (dto.getAmount() == null) {
            violations.add("amount is required");
        } else if (dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            violations.add("amount must be positive (got: " + dto.getAmount() + ")");
        }
        if (dto.getCurrency() == null || dto.getCurrency().trim().length() != 3) {
            violations.add("currency must be a 3-character code");
        }

        if (!violations.isEmpty()) {
            log.warn("Validation failed for transactionId={}, requestId={}: {}",
                    dto.getTransactionId(), dto.getRequestId(), violations);
        }

        return violations;
    }

    /**
     * Validate or throw. Used when a fast-fail is desired.
     */
    public void validateOrThrow(TransactionRequestDTO dto) {
        List<String> violations = validate(dto);
        if (!violations.isEmpty()) {
            throw new TransactionValidationException(
                    "Validation failed for " + dto.getTransactionId() + ": " + violations);
        }
    }
}
