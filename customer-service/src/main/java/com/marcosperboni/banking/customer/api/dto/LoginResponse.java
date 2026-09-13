package com.marcosperboni.banking.customer.api.dto;

public record LoginResponse(String token, long expiresInMs) {
}
