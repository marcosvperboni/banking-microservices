package com.marcosperboni.banking.account.application;

import com.marcosperboni.banking.account.domain.Account;
import com.marcosperboni.banking.account.domain.AccountStatus;
import com.marcosperboni.banking.account.domain.AccountType;
import com.marcosperboni.banking.account.domain.repository.AccountRepository;
import com.marcosperboni.banking.account.infrastructure.persistence.AccountNumberGenerator;
import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    private AccountService accountService;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, accountNumberGenerator);
    }

    @Test
    void open_startsAtZeroBalance() {
        when(accountNumberGenerator.generate()).thenReturn("1234567890");
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.open(customerId, AccountType.CHECKING);

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void credit_increasesBalance() {
        Account account = new Account(customerId, "1234567890", AccountType.CHECKING);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.credit(account.getId(), new BigDecimal("50.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void debit_reducesBalance() {
        Account account = new Account(customerId, "1234567890", AccountType.CHECKING);
        account.credit(new BigDecimal("100.00"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.debit(account.getId(), new BigDecimal("30.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void debit_beyondBalance_throwsAndLeavesBalanceUnchanged() {
        Account account = new Account(customerId, "1234567890", AccountType.CHECKING);
        account.credit(new BigDecimal("20.00"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.debit(account.getId(), new BigDecimal("20.01")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("20.00");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
