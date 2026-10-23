package com.fde.transaction.exception;

/**
 * Thrown when a transaction request fails input validation.
 */
public class TransactionValidationException extends RuntimeException {

    public TransactionValidationException(String message) {
        super(message);
    }
}
