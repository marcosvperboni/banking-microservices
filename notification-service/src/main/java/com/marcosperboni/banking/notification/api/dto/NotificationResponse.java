package com.marcosperboni.banking.notification.api.dto;

import com.marcosperboni.banking.notification.domain.Notification;
import com.marcosperboni.banking.notification.domain.NotificationChannel;
import com.marcosperboni.banking.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID accountId,
        UUID transactionId,
        NotificationChannel channel,
        String message,
        NotificationStatus status,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getAccountId(),
                notification.getTransactionId(),
                notification.getChannel(),
                notification.getMessage(),
                notification.getStatus(),
                notification.getCreatedAt());
    }
}
