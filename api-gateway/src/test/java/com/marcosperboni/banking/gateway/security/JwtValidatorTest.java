package com.marcosperboni.banking.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtValidator validator = new JwtValidator(properties);

    @Test
    void rejectsGarbageToken() {
        assertThat(validator.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsEmptyToken() {
        assertThat(validator.isValid("")).isFalse();
    }

    @Test
    void acceptsTokenSignedWithSameSecret() {
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("customer-123")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        properties.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThat(validator.isValid(token)).isTrue();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("customer-123")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "a-totally-different-secret-key-32-bytes!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThat(validator.isValid(token)).isFalse();
    }
}
