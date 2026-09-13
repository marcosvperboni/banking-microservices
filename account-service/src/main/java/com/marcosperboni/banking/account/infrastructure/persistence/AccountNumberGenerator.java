package com.marcosperboni.banking.account.infrastructure.persistence;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates a 10-digit numeric account number. Collision odds are astronomically low; no retry loop needed. */
@Component
public class AccountNumberGenerator {

    private static final long MIN = 1_000_000_000L;
    private static final long RANGE = 9_000_000_000L;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return String.valueOf(MIN + random.nextLong(RANGE));
    }
}
