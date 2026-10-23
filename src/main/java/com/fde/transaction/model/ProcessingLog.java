package com.fde.transaction.model;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit log for every processing action taken on a transaction.
 */
@Entity
@Table(name = "processing_log")
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "result", length = 500)
    private String result;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        this.loggedAt = LocalDateTime.now();
    }
}
