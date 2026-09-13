package com.marcosperboni.banking.account.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosperboni.banking.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accountdb")
            .withUsername("banking")
            .withPassword("banking");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearerToken(UUID customerId) {
        return jwtTokenProvider.generateToken(customerId.toString(), List.of("ROLE_CUSTOMER"));
    }

    private String openAccount(UUID customerId, String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("customerId", customerId.toString(), "type", "CHECKING"));
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(comparesEqualTo(0.00)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void opensAccount_creditsIt_andReflectsFreshBalanceAfterEachCredit() throws Exception {
        UUID customerId = UUID.randomUUID();
        String bearer = bearerToken(customerId);

        String accountId = openAccount(customerId, bearer);

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/credit")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "100.00", "reason", "deposito inicial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(comparesEqualTo(100.00)));

        mockMvc.perform(get("/api/v1/accounts/" + accountId + "/balance")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(comparesEqualTo(100.00)));

        // Second credit must evict the cached balance, not leave the stale first reading behind.
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/credit")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "50.00", "reason", "deposito extra"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/" + accountId + "/balance")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(comparesEqualTo(150.00)));

        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/debit")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "1000.00", "reason", "saque excessivo"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gettingAnotherCustomersAccount_isForbidden() throws Exception {
        UUID owner = UUID.randomUUID();
        String ownerToken = bearerToken(owner);
        String accountId = openAccount(owner, ownerToken);

        String otherToken = bearerToken(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
