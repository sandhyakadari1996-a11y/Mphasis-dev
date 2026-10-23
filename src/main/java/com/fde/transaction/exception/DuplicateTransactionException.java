package com.fde.transaction.exception;

/**
 * Thrown when a duplicate request_id or transaction_id is detected.
 */
public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String message) {
        super(message);
    }

    public DuplicateTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
