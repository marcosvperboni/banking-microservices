package com.marcosperboni.banking.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Plain data-holding aggregate. The transfer workflow (calling account-service,
 * compensating, deciding the final status) is a cross-aggregate saga owned by
 * {@code application.TransferService}, not by this entity.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private Transaction(String idempotencyKey, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        this.idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        this.sourceAccountId = requireNonNull(sourceAccountId, "sourceAccountId");
        this.targetAccountId = requireNonNull(targetAccountId, "targetAccountId");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.type = TransactionType.TRANSFER;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static Transaction pending(String idempotencyKey, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        return new Transaction(idempotencyKey, sourceAccountId, targetAccountId, amount);
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static UUID requireNonNull(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
