package com.marcosperboni.banking.account.api.dto;

import com.marcosperboni.banking.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotNull AccountType type) {
}
