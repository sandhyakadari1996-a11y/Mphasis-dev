package com.fde.transaction.model;

import com.fde.transaction.enums.TransactionStatus;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing an incoming transaction request.
 * Unique constraints on transactionId and requestId enforce idempotency at the DB level.
 */
@Entity
@Table(name = "transaction_requests",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_transaction_id", columnNames = "transaction_id"),
           @UniqueConstraint(name = "uk_request_id", columnNames = "request_id")
       })
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private com.fde.transaction.enums.TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
