package com.marcosperboni.banking.notification.infrastructure.messaging;

import com.marcosperboni.banking.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topic.transaction-events:transaction-events}", groupId = "notification-service")
    public void onTransactionEvent(TransactionCompletedEvent event) {
        try {
            notificationService.handleTransactionEvent(event);
        } catch (Exception ex) {
            // ponytail: swallow-and-log so one malformed/invalid event doesn't kill the
            // consumer thread; upgrade to a dead-letter topic if bad messages recur.
            log.error("Failed to process transaction event {}", event, ex);
        }
    }
}
