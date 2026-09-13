package com.marcosperboni.banking.transaction.api.dto;

import com.marcosperboni.banking.transaction.domain.Transaction;
import com.marcosperboni.banking.transaction.domain.TransactionStatus;
import com.marcosperboni.banking.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt());
    }
}
