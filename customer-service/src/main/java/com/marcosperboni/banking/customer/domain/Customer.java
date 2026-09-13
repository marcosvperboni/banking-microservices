package com.marcosperboni.banking.customer.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "document_number", nullable = false, unique = true)
    private String documentNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_roles", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    private Customer(String fullName, String email, String documentNumber, String passwordHash) {
        this.fullName = requireNonBlank(fullName, "fullName");
        this.email = requireNonBlank(email, "email");
        this.documentNumber = requireNonBlank(documentNumber, "documentNumber");
        this.passwordHash = requireNonBlank(passwordHash, "passwordHash");
        this.roles.add("ROLE_CUSTOMER");
        this.createdAt = Instant.now();
    }

    public static Customer register(String fullName, String email, String documentNumber, String passwordHash) {
        return new Customer(fullName, email, documentNumber, passwordHash);
    }

    public void updateProfile(String fullName, String email) {
        this.fullName = requireNonBlank(fullName, "fullName");
        this.email = requireNonBlank(email, "email");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
