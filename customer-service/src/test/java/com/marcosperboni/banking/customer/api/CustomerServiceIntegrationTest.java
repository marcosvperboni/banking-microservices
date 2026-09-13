package com.marcosperboni.banking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosperboni.banking.customer.api.dto.LoginRequest;
import com.marcosperboni.banking.customer.api.dto.LoginResponse;
import com.marcosperboni.banking.customer.api.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.jayway.jsonpath.JsonPath.read;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class CustomerServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("customerdb")
            .withUsername("banking")
            .withPassword("banking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerLoginAndFetchOwnProfile() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Alice Smith", "alice@example.com", "11122233344", "S3cret!23");

        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andReturn().getResponse().getContentAsString();

        String customerId = read(registerBody, "$.id");

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "S3cret!23");
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(loginBody, LoginResponse.class);

        mockMvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", "Bearer " + loginResponse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.id", is(customerId)));
    }

    @Test
    void forbidsAccessToAnotherCustomersProfile() throws Exception {
        RegisterRequest ownerRequest = new RegisterRequest("Bob Owner", "bob@example.com", "22233344455", "S3cret!23");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(ownerRequest)));

        LoginRequest loginRequest = new LoginRequest("bob@example.com", "S3cret!23");
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(loginBody, LoginResponse.class);

        UUID someoneElseId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/customers/" + someoneElseId)
                        .header("Authorization", "Bearer " + loginResponse.token()))
                .andExpect(status().isForbidden());
    }
}
