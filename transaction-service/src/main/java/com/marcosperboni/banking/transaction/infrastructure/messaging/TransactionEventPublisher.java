package com.marcosperboni.banking.transaction.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventPublisher {

    private final KafkaTemplate<String, TransactionCompletedEvent> kafkaTemplate;
    private final String topic;

    public TransactionEventPublisher(KafkaTemplate<String, TransactionCompletedEvent> kafkaTemplate,
                                      @Value("${app.kafka.topic.transaction-events:transaction-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(TransactionCompletedEvent event) {
        kafkaTemplate.send(topic, event.transactionId().toString(), event);
    }
}
