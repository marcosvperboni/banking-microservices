package com.marcosperboni.banking.transaction.application;

import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import com.marcosperboni.banking.transaction.domain.Transaction;
import com.marcosperboni.banking.transaction.domain.TransactionStatus;
import com.marcosperboni.banking.transaction.domain.repository.TransactionRepository;
import com.marcosperboni.banking.transaction.infrastructure.client.AccountClient;
import com.marcosperboni.banking.transaction.infrastructure.messaging.TransactionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String IDEMPOTENCY_KEY = "idem-123";
    private static final String AUTH_HEADER = "Bearer token";
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountClient accountClient;
    @Mock
    private TransactionEventPublisher eventPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transactionRepository, accountClient, eventPublisher, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath_completesTransferAndPublishesEvent() {
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        Transaction result = transferService.transfer(
                new TransferCommand(SOURCE, TARGET, AMOUNT), IDEMPOTENCY_KEY, AUTH_HEADER);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(accountClient).debit(eq(SOURCE), eq(AMOUNT), anyString(), eq(AUTH_HEADER));
        verify(accountClient).credit(eq(TARGET), eq(AMOUNT), anyString(), eq(AUTH_HEADER));
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.argThat(
                event -> event.status() == TransactionStatus.COMPLETED));
    }

    @Test
    void debitFailure_marksFailedAndDoesNotCallCredit() {
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        doThrow(new BusinessRuleException("Saldo insuficiente na conta de origem"))
                .when(accountClient).debit(eq(SOURCE), eq(AMOUNT), anyString(), eq(AUTH_HEADER));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferCommand(SOURCE, TARGET, AMOUNT), IDEMPOTENCY_KEY, AUTH_HEADER))
                .isInstanceOf(BusinessRuleException.class);

        verify(accountClient, never()).credit(any(), any(), anyString(), anyString());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.argThat(
                event -> event.status() == TransactionStatus.FAILED));
    }

    @Test
    void creditFailureAfterDebit_compensatesAndMarksFailed() {
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        doThrow(new ResourceNotFoundException("Conta nao encontrada"))
                .when(accountClient).credit(eq(TARGET), eq(AMOUNT), anyString(), eq(AUTH_HEADER));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferCommand(SOURCE, TARGET, AMOUNT), IDEMPOTENCY_KEY, AUTH_HEADER))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(accountClient).debit(eq(SOURCE), eq(AMOUNT), anyString(), eq(AUTH_HEADER));
        verify(accountClient).credit(eq(SOURCE), eq(AMOUNT), anyString(), eq(AUTH_HEADER));
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void sameAccountTransfer_rejectedBeforeAnyHttpCall() {
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(
                new TransferCommand(SOURCE, SOURCE, AMOUNT), IDEMPOTENCY_KEY, AUTH_HEADER))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(accountClient);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void idempotentReplay_returnsStoredResultWithoutCallingAccountClient() {
        Transaction existing = Transaction.pending(IDEMPOTENCY_KEY, SOURCE, TARGET, AMOUNT);
        existing.markCompleted();
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        Transaction result = transferService.transfer(
                new TransferCommand(SOURCE, TARGET, AMOUNT), IDEMPOTENCY_KEY, AUTH_HEADER);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(accountClient);
        verify(transactionRepository, never()).saveAndFlush(any());
    }
}
