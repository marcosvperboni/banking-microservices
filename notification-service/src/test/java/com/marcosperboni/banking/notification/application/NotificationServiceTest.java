package com.marcosperboni.banking.notification.application;

import com.marcosperboni.banking.notification.domain.Notification;
import com.marcosperboni.banking.notification.domain.NotificationStatus;
import com.marcosperboni.banking.notification.domain.repository.NotificationRepository;
import com.marcosperboni.banking.notification.infrastructure.messaging.TransactionCompletedEvent;
import com.marcosperboni.banking.notification.infrastructure.messaging.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID targetAccountId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @Test
    void completedTransferNotifiesBothAccountsFromEachPerspective() {
        notificationService = new NotificationService(notificationRepository);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId, sourceAccountId, targetAccountId,
                new BigDecimal("123.45"), TransactionStatus.COMPLETED, Instant.now());

        notificationService.handleTransactionEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();

        Notification sourceNotification = findByAccount(saved, sourceAccountId);
        Notification targetNotification = findByAccount(saved, targetAccountId);

        assertThat(sourceNotification.getMessage()).isEqualTo("Transferencia de R$ 123.45 enviada com sucesso");
        assertThat(targetNotification.getMessage()).isEqualTo("Transferencia de R$ 123.45 recebida");
        assertThat(sourceNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(targetNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sourceNotification.getTransactionId()).isEqualTo(transactionId);
        assertThat(targetNotification.getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    void failedTransferNotifiesBothAccountsWithFailureMessage() {
        notificationService = new NotificationService(notificationRepository);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId, sourceAccountId, targetAccountId,
                new BigDecimal("50.00"), TransactionStatus.FAILED, Instant.now());

        notificationService.handleTransactionEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();

        assertThat(saved).allSatisfy(notification -> {
            assertThat(notification.getMessage()).isEqualTo("Falha na transferencia de R$ 50.00");
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        });
    }

    @Test
    void rejectsEventMissingRequiredFields() {
        notificationService = new NotificationService(notificationRepository);
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                null, sourceAccountId, targetAccountId, new BigDecimal("10.00"), TransactionStatus.COMPLETED, Instant.now());

        assertThatThrownBy(() -> notificationService.handleTransactionEvent(event))
                .isInstanceOf(NullPointerException.class);
    }

    private static Notification findByAccount(List<Notification> notifications, UUID accountId) {
        return notifications.stream()
                .filter(n -> n.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow();
    }
}
