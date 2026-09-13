package com.marcosperboni.banking.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Shared HMAC secret. Must be identical across every service so tokens
     * issued by customer-service validate in account/transaction/notification/gateway.
     */
    private String secret = "change-me-change-me-change-me-change-me-32b";

    private long expirationMs = 3_600_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
