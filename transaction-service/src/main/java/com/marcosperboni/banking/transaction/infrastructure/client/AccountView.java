package com.marcosperboni.banking.transaction.infrastructure.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Anti-corruption-layer DTO mirroring account-service's account representation.
 * Owned by transaction-service; not shared code.
 */
public record AccountView(
        UUID id,
        UUID customerId,
        String accountNumber,
        String type,
        BigDecimal balance,
        String status
) {
}
