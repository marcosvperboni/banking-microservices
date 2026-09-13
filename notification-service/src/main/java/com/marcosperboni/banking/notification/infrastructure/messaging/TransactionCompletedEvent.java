package com.marcosperboni.banking.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the JSON payload published by transaction-service on the
 * {@code transaction-events} topic. Field names must match exactly since
 * consumption relies on Spring Kafka's default-type JSON deserialization
 * (no type headers), see application.yml.
 */
public record TransactionCompletedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        TransactionStatus status,
        Instant occurredAt) {
}
