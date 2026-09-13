package com.marcosperboni.banking.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmountRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String reason) {
}
