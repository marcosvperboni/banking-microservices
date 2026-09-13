package com.marcosperboni.banking.notification.application;

import com.marcosperboni.banking.notification.domain.Notification;
import com.marcosperboni.banking.notification.domain.NotificationChannel;
import com.marcosperboni.banking.notification.domain.NotificationStatus;
import com.marcosperboni.banking.notification.domain.repository.NotificationRepository;
import com.marcosperboni.banking.notification.infrastructure.messaging.TransactionCompletedEvent;
import com.marcosperboni.banking.notification.infrastructure.messaging.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Builds and persists one notification per affected account (source and
     * target), then simulates the send by logging it. Never throws once the
     * event itself is well-formed; the caller ({@code TransactionEventListener})
     * is responsible for isolating infrastructure-level failures.
     */
    public void handleTransactionEvent(TransactionCompletedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(event.transactionId(), "transactionId must not be null");
        Objects.requireNonNull(event.sourceAccountId(), "sourceAccountId must not be null");
        Objects.requireNonNull(event.targetAccountId(), "targetAccountId must not be null");
        Objects.requireNonNull(event.amount(), "amount must not be null");
        Objects.requireNonNull(event.status(), "status must not be null");

        String amountLabel = formatAmount(event.amount());
        boolean completed = event.status() == TransactionStatus.COMPLETED;

        String sourceMessage = completed
                ? "Transferencia de " + amountLabel + " enviada com sucesso"
                : "Falha na transferencia de " + amountLabel;
        String targetMessage = completed
                ? "Transferencia de " + amountLabel + " recebida"
                : "Falha na transferencia de " + amountLabel;

        notify(event.sourceAccountId(), event.transactionId(), sourceMessage);
        notify(event.targetAccountId(), event.transactionId(), targetMessage);
    }

    private void notify(UUID accountId, UUID transactionId, String message) {
        Notification notification = Notification.create(
                accountId, transactionId, NotificationChannel.EMAIL, message, NotificationStatus.SENT);
        notificationRepository.save(notification);
        log.info("Notificacao (EMAIL) para conta {}: {}", accountId, message);
    }

    private String formatAmount(BigDecimal amount) {
        return String.format(Locale.US, "R$ %.2f", amount);
    }
}
