package com.marcosperboni.banking.customer.application;

import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.security.JwtProperties;
import com.marcosperboni.banking.common.security.JwtTokenProvider;
import com.marcosperboni.banking.customer.domain.Customer;
import com.marcosperboni.banking.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private RegisterCommand registerCommand;

    @BeforeEach
    void setUp() {
        registerCommand = new RegisterCommand("Jane Doe", "jane@example.com", "12345678901", "S3cret!23");
    }

    @Test
    void registersNewCustomer() {
        when(customerRepository.existsByEmail(registerCommand.email())).thenReturn(false);
        when(customerRepository.existsByDocumentNumber(registerCommand.documentNumber())).thenReturn(false);
        when(passwordEncoder.encode(registerCommand.password())).thenReturn("hashed-password");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = authService.register(registerCommand);

        assertThat(customer.getEmail()).isEqualTo("jane@example.com");
        assertThat(customer.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(customer.getRoles()).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void rejectsDuplicateEmail() {
        when(customerRepository.existsByEmail(registerCommand.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerCommand))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsDuplicateDocumentNumber() {
        when(customerRepository.existsByEmail(registerCommand.email())).thenReturn(false);
        when(customerRepository.existsByDocumentNumber(registerCommand.documentNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerCommand))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void loginReturnsTokenOnValidCredentials() {
        Customer customer = Customer.register("Jane Doe", "jane@example.com", "12345678901", "hashed-password");
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("S3cret!23", "hashed-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyString(), any(List.class))).thenReturn("jwt-token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);

        LoginResult result = authService.login("jane@example.com", "S3cret!23");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.expiresInMs()).isEqualTo(3600000L);
    }

    @Test
    void loginFailsWithWrongPassword() {
        Customer customer = Customer.register("Jane Doe", "jane@example.com", "12345678901", "hashed-password");
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("jane@example.com", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginFailsWithUnknownEmail() {
        when(customerRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@example.com", "any-password"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
