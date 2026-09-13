package com.marcosperboni.banking.customer.application;

import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.security.JwtProperties;
import com.marcosperboni.banking.common.security.JwtTokenProvider;
import com.marcosperboni.banking.customer.domain.Customer;
import com.marcosperboni.banking.customer.domain.repository.CustomerRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public Customer register(RegisterCommand command) {
        if (customerRepository.existsByEmail(command.email())) {
            throw new BusinessRuleException("Email already registered: " + command.email());
        }
        if (customerRepository.existsByDocumentNumber(command.documentNumber())) {
            throw new BusinessRuleException("Document number already registered: " + command.documentNumber());
        }
        Customer customer = Customer.register(
                command.fullName(),
                command.email(),
                command.documentNumber(),
                passwordEncoder.encode(command.password()));
        return customerRepository.save(customer);
    }

    public LoginResult login(String email, String password) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String token = jwtTokenProvider.generateToken(customer.getId().toString(), List.copyOf(customer.getRoles()));
        return new LoginResult(token, jwtProperties.getExpirationMs());
    }
}
