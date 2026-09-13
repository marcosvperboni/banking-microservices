package com.marcosperboni.banking.account.api;

import com.marcosperboni.banking.account.api.dto.AccountResponse;
import com.marcosperboni.banking.account.api.dto.AmountRequest;
import com.marcosperboni.banking.account.api.dto.BalanceResponse;
import com.marcosperboni.banking.account.api.dto.OpenAccountRequest;
import com.marcosperboni.banking.account.application.AccountService;
import com.marcosperboni.banking.account.domain.Account;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("#request.customerId().toString() == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.open(request.customerId(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(@PathVariable UUID id, Authentication authentication) {
        Account account = accountService.getById(id);
        checkOwnership(account.getCustomerId(), authentication);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id, Authentication authentication) {
        Account account = accountService.getById(id);
        checkOwnership(account.getCustomerId(), authentication);
        return ResponseEntity.ok(new BalanceResponse(id, accountService.getBalance(id)));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listByCustomer(@RequestParam UUID customerId,
                                                                  Authentication authentication) {
        checkOwnership(customerId, authentication);
        List<AccountResponse> accounts = accountService.listByCustomer(customerId).stream()
                .map(AccountResponse::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@PathVariable UUID id, Authentication authentication) {
        Account account = accountService.getById(id);
        checkOwnership(account.getCustomerId(), authentication);
        accountService.close(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<AccountResponse> credit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        Account account = accountService.credit(id, request.amount());
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<AccountResponse> debit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        Account account = accountService.debit(id, request.amount());
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    private void checkOwnership(UUID customerId, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !customerId.toString().equals(authentication.getName())) {
            throw new AccessDeniedException("Acesso negado a conta de outro cliente");
        }
    }
}
