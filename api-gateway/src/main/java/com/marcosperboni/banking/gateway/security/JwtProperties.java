package com.marcosperboni.banking.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mirrors common-security's JwtProperties. Duplicated (not reused) on purpose:
 * common-security drags in spring-boot-starter-web + servlet security, which
 * conflicts with this module's reactive WebFlux runtime. The secret value
 * MUST stay identical to the other services' jwt.secret for tokens to validate.
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret = "change-me-change-me-change-me-change-me-32b";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
