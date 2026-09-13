package com.marcosperboni.banking.notification;

import com.marcosperboni.banking.common.security.JwtTokenProvider;
import com.marcosperboni.banking.notification.domain.Notification;
import com.marcosperboni.banking.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notificationdb")
            .withUsername("banking")
            .withPassword("banking");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static KafkaProducer<String, String> producer;

    @AfterAll
    static void stopProducer() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void consumesTransactionEventAndExposesNotificationHistory() throws Exception {
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        String json = """
                {"transactionId":"%s","sourceAccountId":"%s","targetAccountId":"%s","amount":123.45,"status":"COMPLETED","occurredAt":"2026-01-01T12:00:00Z"}
                """.formatted(transactionId, sourceAccountId, targetAccountId).strip();

        producer().send(new ProducerRecord<>("transaction-events", json));
        producer().flush();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notificationRepository.findByAccountId(sourceAccountId, PageRequest.of(0, 10)).getTotalElements())
                    .isEqualTo(1);
            assertThat(notificationRepository.findByAccountId(targetAccountId, PageRequest.of(0, 10)).getTotalElements())
                    .isEqualTo(1);
        });

        Notification sourceNotification = notificationRepository.findByAccountId(sourceAccountId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(sourceNotification.getMessage()).isEqualTo("Transferencia de R$ 123.45 enviada com sucesso");
        assertThat(sourceNotification.getTransactionId()).isEqualTo(transactionId);

        String token = jwtTokenProvider.generateToken(sourceAccountId.toString(), java.util.List.of("ROLE_CUSTOMER"));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("accountId", sourceAccountId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Transferencia de R$ 123.45 enviada com sucesso")));
    }

    private static synchronized KafkaProducer<String, String> producer() {
        if (producer == null) {
            Map<String, Object> props = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
            props.put("value.serializer", StringSerializer.class);
            props.put("key.serializer", StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        return producer;
    }
}
