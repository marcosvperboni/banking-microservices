package com.marcosperboni.banking.common.exception;

/** Raised when a domain invariant is violated (e.g. insufficient funds, duplicate account). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
