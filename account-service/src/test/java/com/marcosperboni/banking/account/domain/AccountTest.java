package com.marcosperboni.banking.account.domain;

import com.marcosperboni.banking.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account newAccount() {
        return new Account(UUID.randomUUID(), "1234567890", AccountType.CHECKING);
    }

    @Test
    void opensWithZeroBalanceAndActiveStatus() {
        Account account = newAccount();
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void creditIncreasesBalance() {
        Account account = newAccount();
        account.credit(new BigDecimal("100.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitDecreasesBalance() {
        Account account = newAccount();
        account.credit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("40.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitBeyondBalanceThrowsAndLeavesBalanceUnchanged() {
        Account account = newAccount();
        account.credit(new BigDecimal("30.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("30.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Saldo insuficiente");
        assertThat(account.getBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    void creditWithNonPositiveAmountThrows() {
        Account account = newAccount();
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void debitWithNonPositiveAmountThrows() {
        Account account = newAccount();
        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void close_setsStatusClosed() {
        Account account = newAccount();
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }
}
