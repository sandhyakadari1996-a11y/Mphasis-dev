package com.fde.transaction.dto;

import com.fde.transaction.enums.TransactionType;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for incoming transaction submission.
 * Validation rules enforced at the REST boundary.
 */
@Data
public class TransactionRequestDTO {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotBlank(message = "requestId is required")
    private String requestId;

    @NotBlank(message = "accountId is required")
    private String accountId;

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be 3 characters")
    private String currency;

    private Integer sequenceNumber;
    private LocalDateTime eventTime;
}
