package com.marcosperboni.banking.customer.api.dto;

import com.marcosperboni.banking.customer.domain.Customer;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String email,
        String documentNumber,
        Set<String> roles,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getDocumentNumber(),
                customer.getRoles(),
                customer.getCreatedAt());
    }
}
