package com.marcosperboni.banking.account.application;

import com.marcosperboni.banking.account.domain.Account;
import com.marcosperboni.banking.account.domain.AccountType;
import com.marcosperboni.banking.account.domain.repository.AccountRepository;
import com.marcosperboni.banking.account.infrastructure.persistence.AccountNumberGenerator;
import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public AccountService(AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    @Transactional
    public Account open(UUID customerId, AccountType type) {
        Account account = new Account(customerId, accountNumberGenerator.generate(), type);
        return accountRepository.save(account);
    }

    public Account getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta nao encontrada: " + id));
    }

    @Cacheable(value = "balance", key = "#id")
    public BigDecimal getBalance(UUID id) {
        return getById(id).getBalance();
    }

    public List<Account> listByCustomer(UUID customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    @CacheEvict(value = "balance", key = "#id")
    @Transactional
    public Account credit(UUID id, BigDecimal amount) {
        Account account = getById(id);
        account.credit(amount);
        return accountRepository.save(account);
    }

    @CacheEvict(value = "balance", key = "#id")
    @Transactional
    public Account debit(UUID id, BigDecimal amount) {
        Account account = getById(id);
        account.debit(amount);
        return accountRepository.save(account);
    }

    @CacheEvict(value = "balance", key = "#id")
    @Transactional
    public void close(UUID id) {
        Account account = getById(id);
        account.close();
        accountRepository.save(account);
    }
}
