package com.marcosperboni.banking.transaction.application;

import com.marcosperboni.banking.common.exception.BusinessRuleException;
import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import com.marcosperboni.banking.transaction.domain.Transaction;
import com.marcosperboni.banking.transaction.domain.TransactionStatus;
import com.marcosperboni.banking.transaction.domain.repository.TransactionRepository;
import com.marcosperboni.banking.transaction.infrastructure.client.AccountClient;
import com.marcosperboni.banking.transaction.infrastructure.messaging.TransactionCompletedEvent;
import com.marcosperboni.banking.transaction.infrastructure.messaging.TransactionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Owns the transfer saga: debit source, credit target, compensate on partial
 * failure. Deliberately NOT wrapped in a single @Transactional block, since the
 * workflow spans slow external HTTP calls to account-service and a long-held DB
 * transaction across that I/O would be a resource-holding anti-pattern; each
 * repository call below commits independently.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String LOCK_PREFIX = "idem:transfer:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int POLL_ATTEMPTS = 5;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final TransactionEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    public TransferService(TransactionRepository transactionRepository,
                            AccountClient accountClient,
                            TransactionEventPublisher eventPublisher,
                            StringRedisTemplate redisTemplate) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
    }

    public boolean idempotencyKeyExists(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    public Transaction transfer(TransferCommand command, String idempotencyKey, String authorizationHeader) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        // ponytail: single SETNX lock without a per-holder token, releasing it in
        // `finally` can drop a lock acquired by a later request once TTL expires
        // mid-flight; upgrade to a token + compare-and-delete Lua script if that
        // race becomes observable in practice.
        boolean acquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL));

        if (!acquired) {
            return pollForExisting(idempotencyKey)
                    .orElseThrow(() -> new BusinessRuleException(
                            "Transferencia em andamento para esta chave de idempotencia, tente novamente"));
        }

        try {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(() -> executeTransfer(command, idempotencyKey, authorizationHeader));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private Transaction executeTransfer(TransferCommand command, String idempotencyKey, String authorizationHeader) {
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            throw new BusinessRuleException("Conta de origem e destino nao podem ser iguais");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O valor da transferencia deve ser maior que zero");
        }

        Transaction transaction = createTransaction(command, idempotencyKey);
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return transaction; // fetched via the unique-constraint safety net: already resolved
        }

        String reason = "Transferencia " + transaction.getId();
        try {
            accountClient.debit(command.sourceAccountId(), command.amount(), reason, authorizationHeader);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            failAndPublish(transaction, "Falha no debito: " + ex.getMessage());
            throw ex;
        }

        try {
            accountClient.credit(command.targetAccountId(), command.amount(), reason, authorizationHeader);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            compensate(transaction, command, authorizationHeader);
            failAndPublish(transaction, "Falha no credito, estorno realizado na conta de origem: " + ex.getMessage());
            throw ex;
        }

        transaction.markCompleted();
        transactionRepository.save(transaction);
        eventPublisher.publish(toEvent(transaction));
        return transaction;
    }

    private Transaction createTransaction(TransferCommand command, String idempotencyKey) {
        Transaction transaction = Transaction.pending(
                idempotencyKey, command.sourceAccountId(), command.targetAccountId(), command.amount());
        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
        }
    }

    private void compensate(Transaction transaction, TransferCommand command, String authorizationHeader) {
        try {
            accountClient.credit(command.sourceAccountId(), command.amount(),
                    "Estorno transferencia " + transaction.getId(), authorizationHeader);
        } catch (RuntimeException ex) {
            log.error("Falha ao compensar transferencia {}: credito de estorno na conta {} nao foi aplicado",
                    transaction.getId(), command.sourceAccountId(), ex);
        }
    }

    private void failAndPublish(Transaction transaction, String reason) {
        transaction.markFailed(reason);
        transactionRepository.save(transaction);
        eventPublisher.publish(toEvent(transaction));
    }

    private Optional<Transaction> pollForExisting(String idempotencyKey) {
        for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
            Optional<Transaction> found = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (found.isPresent()) {
                return found;
            }
            sleep(POLL_INTERVAL);
        }
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private TransactionCompletedEvent toEvent(Transaction transaction) {
        return new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                transaction.getStatus(),
                Instant.now());
    }
}
