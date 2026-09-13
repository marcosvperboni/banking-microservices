package com.marcosperboni.banking.customer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "documentNumber must have 11 digits") String documentNumber,
        @NotBlank @Size(min = 8) String password) {
}
