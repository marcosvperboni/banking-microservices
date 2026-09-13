package com.marcosperboni.banking.customer.application;

public record RegisterCommand(String fullName, String email, String documentNumber, String password) {
}
