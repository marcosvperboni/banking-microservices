package com.marcosperboni.banking.transaction.infrastructure.messaging;

import com.marcosperboni.banking.transaction.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        TransactionStatus status,
        Instant occurredAt
) {
}
