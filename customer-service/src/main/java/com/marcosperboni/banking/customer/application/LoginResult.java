package com.marcosperboni.banking.customer.application;

public record LoginResult(String token, long expiresInMs) {
}
