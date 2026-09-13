package com.marcosperboni.banking.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtTokenProvider provider = new JwtTokenProvider(properties);

    @Test
    void generatesTokenThatValidatesAndCarriesSubjectAndRoles() {
        String token = provider.generateToken("customer-123", List.of("ROLE_CUSTOMER", "ROLE_ADMIN"));

        Optional<Claims> claims = provider.validate(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("customer-123");
        assertThat(provider.extractRoles(claims.get())).containsExactly("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(provider.validate("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsEmptyToken() {
        assertThat(provider.validate("")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-totally-different-secret-key-32-bytes!!");
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

        String token = otherProvider.generateToken("customer-123", List.of("ROLE_CUSTOMER"));

        assertThat(provider.validate(token)).isEmpty();
    }

    @Test
    void extractRolesReturnsEmptyListWhenClaimMissing() {
        String token = provider.generateToken("customer-123", List.of());

        Claims claims = provider.validate(token).orElseThrow();

        assertThat(provider.extractRoles(claims)).isEmpty();
    }
}
