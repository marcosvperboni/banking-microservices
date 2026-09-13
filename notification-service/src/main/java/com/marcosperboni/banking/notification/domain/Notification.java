package com.marcosperboni.banking.notification.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Notification(UUID accountId, UUID transactionId, NotificationChannel channel,
                          String message, NotificationStatus status) {
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.channel = channel;
        this.message = message;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public static Notification create(UUID accountId, UUID transactionId, NotificationChannel channel,
                                       String message, NotificationStatus status) {
        return new Notification(accountId, transactionId, channel, message, status);
    }
}
