package com.marcosperboni.banking.customer.api;

import com.marcosperboni.banking.customer.api.dto.LoginRequest;
import com.marcosperboni.banking.customer.api.dto.LoginResponse;
import com.marcosperboni.banking.customer.api.dto.RegisterRequest;
import com.marcosperboni.banking.customer.api.dto.CustomerResponse;
import com.marcosperboni.banking.customer.application.AuthService;
import com.marcosperboni.banking.customer.application.LoginResult;
import com.marcosperboni.banking.customer.application.RegisterCommand;
import com.marcosperboni.banking.customer.domain.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest request) {
        Customer customer = authService.register(
                new RegisterCommand(request.fullName(), request.email(), request.documentNumber(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(result.token(), result.expiresInMs()));
    }
}
