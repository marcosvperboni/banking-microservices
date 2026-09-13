package com.marcosperboni.banking.transaction.application;

import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import com.marcosperboni.banking.transaction.domain.Transaction;
import com.marcosperboni.banking.transaction.domain.repository.TransactionRepository;
import com.marcosperboni.banking.transaction.infrastructure.client.AccountClient;
import com.marcosperboni.banking.transaction.infrastructure.client.AccountView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ownership is enforced by asking account-service which customer owns the
 * account(s) involved, since this service has no direct access to customerId.
 */
@Service
public class TransactionQueryService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public TransactionQueryService(TransactionRepository transactionRepository, AccountClient accountClient) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
    }

    public Transaction getById(UUID id, String authorizationHeader, Authentication authentication) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacao nao encontrada: " + id));
        if (isAdmin(authentication)
                || ownsAccount(transaction.getSourceAccountId(), authentication, authorizationHeader)
                || ownsAccount(transaction.getTargetAccountId(), authentication, authorizationHeader)) {
            return transaction;
        }
        throw new AccessDeniedException("Acesso negado a esta transacao");
    }

    public Page<Transaction> getStatement(UUID accountId, Pageable pageable, String authorizationHeader, Authentication authentication) {
        if (!isAdmin(authentication) && !ownsAccount(accountId, authentication, authorizationHeader)) {
            throw new AccessDeniedException("Acesso negado a esta conta");
        }
        return transactionRepository.findBySourceAccountIdOrTargetAccountId(accountId, accountId, pageable);
    }

    private boolean ownsAccount(UUID accountId, Authentication authentication, String authorizationHeader) {
        AccountView account = accountClient.getAccount(accountId, authorizationHeader);
        return account.customerId().toString().equals(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
