package com.fde.transaction.enums;

/**
 * Transaction status lifecycle.
 * RECEIVED -> PROCESSING -> PROCESSED | DUPLICATE | FAILED | RETRY_PENDING
 * RETRY_PENDING -> PROCESSING -> PROCESSED | FAILED (on retry exhaustion)
 */
public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    FAILED,
    RETRY_PENDING,
    PENDING
}
