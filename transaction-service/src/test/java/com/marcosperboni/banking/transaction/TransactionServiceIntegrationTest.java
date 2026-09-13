package com.marcosperboni.banking.transaction;

import com.marcosperboni.banking.common.security.JwtTokenProvider;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TransactionServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transactiondb")
            .withUsername("banking")
            .withPassword("banking");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static HttpServer accountServiceStub;

    @BeforeAll
    static void startAccountServiceStub() throws IOException {
        accountServiceStub = HttpServer.create(new InetSocketAddress(0), 0);
        accountServiceStub.createContext("/api/v1/accounts/", exchange -> {
            String body = """
                    {"id":"%s","customerId":"%s","accountNumber":"ACC-1","type":"CHECKING","balance":1000.00,"status":"ACTIVE"}
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        accountServiceStub.start();
    }

    @AfterAll
    static void stopAccountServiceStub() {
        accountServiceStub.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("services.account.base-url", () -> "http://localhost:" + accountServiceStub.getAddress().getPort());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void transferCompletesAndPublishesEvent_replayIsIdempotent() {
        String token = jwtTokenProvider.generateToken(UUID.randomUUID().toString(), List.of("ROLE_CUSTOMER"));
        String idempotencyKey = "it-" + UUID.randomUUID();
        Map<String, Object> requestBody = Map.of(
                "sourceAccountId", UUID.randomUUID().toString(),
                "targetAccountId", UUID.randomUUID().toString(),
                "amount", 150.00);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> firstResponse = restTemplate.postForEntity("/api/v1/transactions/transfer", request, Map.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().get("status")).isEqualTo("COMPLETED");
        String transactionId = (String) firstResponse.getBody().get("id");

        List<ConsumerRecord<String, String>> records = pollTransactionEvents(Duration.ofSeconds(15), 1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo(transactionId);
        assertThat(records.get(0).value()).contains(transactionId).contains("COMPLETED");

        ResponseEntity<Map> replayResponse = restTemplate.postForEntity("/api/v1/transactions/transfer", request, Map.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayResponse.getBody().get("id")).isEqualTo(transactionId);

        // No second transfer was executed (idempotent replay), so a fresh poll from the
        // beginning of the topic must still find exactly one event for this transaction.
        List<ConsumerRecord<String, String>> allRecordsForKey = pollTransactionEvents(Duration.ofSeconds(5), 1);
        long countForTransaction = allRecordsForKey.stream()
                .filter(r -> r.key().equals(transactionId))
                .count();
        assertThat(countForTransaction).isEqualTo(1);
    }

    private List<ConsumerRecord<String, String>> pollTransactionEvents(Duration timeout, int minRecords) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("transaction-events"));
            List<ConsumerRecord<String, String>> collected = new java.util.ArrayList<>();
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && collected.size() < minRecords) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.forEach(collected::add);
            }
            return collected;
        }
    }
}
