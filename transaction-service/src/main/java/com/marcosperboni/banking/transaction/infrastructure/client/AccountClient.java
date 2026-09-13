package com.marcosperboni.banking.transaction.infrastructure.client;

import com.marcosperboni.banking.common.dto.ErrorResponse;
import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thin HTTP client wrapping account-service's REST contract. Forwards the caller's
 * bearer token, since account-service enforces the same JWT authentication.
 */
@Component
public class AccountClient {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient restClient;

    public AccountClient(@Value("${services.account.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public AccountView getAccount(UUID accountId, String authorizationHeader) {
        try {
            return restClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .header(HttpHeaders.AUTHORIZATION, normalize(authorizationHeader))
                    .retrieve()
                    .body(AccountView.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notFound(accountId);
            }
            throw ex;
        }
    }

    public AccountView credit(UUID accountId, BigDecimal amount, String reason, String authorizationHeader) {
        try {
            return post(accountId, "credit", amount, reason, authorizationHeader);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notFound(accountId);
            }
            if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new BusinessRuleException(extractMessage(ex, "Regra de negocio violada ao creditar a conta " + accountId));
            }
            throw ex;
        }
    }

    public AccountView debit(UUID accountId, BigDecimal amount, String reason, String authorizationHeader) {
        try {
            return post(accountId, "debit", amount, reason, authorizationHeader);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw notFound(accountId);
            }
            if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new BusinessRuleException("Saldo insuficiente na conta de origem");
            }
            throw ex;
        }
    }

    private AccountView post(UUID accountId, String operation, BigDecimal amount, String reason, String authorizationHeader) {
        return restClient.post()
                .uri("/api/v1/accounts/{id}/{operation}", accountId, operation)
                .header(HttpHeaders.AUTHORIZATION, normalize(authorizationHeader))
                .body(new AmountRequest(amount, reason))
                .retrieve()
                .body(AccountView.class);
    }

    private ResourceNotFoundException notFound(UUID accountId) {
        return new ResourceNotFoundException("Conta nao encontrada: " + accountId);
    }

    private String extractMessage(HttpClientErrorException ex, String fallback) {
        try {
            ErrorResponse body = ex.getResponseBodyAs(ErrorResponse.class);
            return body != null && body.message() != null ? body.message() : fallback;
        } catch (RuntimeException parseError) {
            return fallback;
        }
    }

    private String normalize(String authorizationHeader) {
        String token = authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : authorizationHeader;
        return BEARER_PREFIX + token;
    }

    private record AmountRequest(BigDecimal amount, String reason) {
    }
}
