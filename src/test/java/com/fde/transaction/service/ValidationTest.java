package com.fde.transaction.service;

import com.fde.transaction.dto.TransactionRequestDTO;
import com.fde.transaction.enums.TransactionType;
import com.fde.transaction.exception.TransactionValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation tests: verify that invalid requests are rejected with
 * clear violation messages.
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationTest {

    @Autowired
    private TransactionValidationService validator;

    @Test
    @DisplayName("Valid transaction should pass with no violations")
    void validTransactionShouldPass() {
        TransactionRequestDTO dto = buildValidDTO();
        List<String> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Negative amount should be rejected")
    void negativeAmountShouldBeRejected() {
        TransactionRequestDTO dto = buildValidDTO();
        dto.setAmount(new BigDecimal("-50.00"));
        List<String> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.contains("amount must be positive"));
    }

    @Test
    @DisplayName("Zero amount should be rejected")
    void zeroAmountShouldBeRejected() {
        TransactionRequestDTO dto = buildValidDTO();
        dto.setAmount(BigDecimal.ZERO);
        List<String> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.contains("amount must be positive"));
    }

    @Test
    @DisplayName("Missing transactionId should be flagged")
    void missingTransactionIdShouldBeFlagged() {
        TransactionRequestDTO dto = buildValidDTO();
        dto.setTransactionId(null);
        List<String> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.contains("transactionId"));
    }

    @Test
    @DisplayName("Invalid currency length should be flagged")
    void invalidCurrencyShouldBeFlagged() {
        TransactionRequestDTO dto = buildValidDTO();
        dto.setCurrency("US");
        List<String> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.contains("currency"));
    }

    @Test
    @DisplayName("Multiple violations should all be reported")
    void multipleViolationsShouldAllBeReported() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.setAmount(new BigDecimal("10.00"));
        // Everything else is null

        List<String> violations = validator.validate(dto);
        assertThat(violations).hasSizeGreaterThan(2);
    }

    @Test
    @DisplayName("validateOrThrow should throw on invalid input")
    void validateOrThrowShouldThrow() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        assertThatThrownBy(() -> validator.validateOrThrow(dto))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("Validation failed");
    }

    private TransactionRequestDTO buildValidDTO() {
        // Use values matching your sample_data.sql (TXN-0001 format)
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.setTransactionId("TXN-0001");
        dto.setRequestId("REQ-001");
        dto.setAccountId("ACC-100");
        dto.setTransactionType(TransactionType.CREDIT);
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setCurrency("USD");
        dto.setSequenceNumber(1);
        return dto;
    }
}
