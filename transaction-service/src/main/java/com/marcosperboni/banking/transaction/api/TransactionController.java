package com.marcosperboni.banking.transaction.api;

import com.marcosperboni.banking.transaction.api.dto.TransactionResponse;
import com.marcosperboni.banking.transaction.api.dto.TransferRequest;
import com.marcosperboni.banking.transaction.application.TransactionQueryService;
import com.marcosperboni.banking.transaction.application.TransferCommand;
import com.marcosperboni.banking.transaction.application.TransferService;
import com.marcosperboni.banking.transaction.domain.Transaction;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransferService transferService;
    private final TransactionQueryService queryService;

    public TransactionController(TransferService transferService, TransactionQueryService queryService) {
        this.transferService = transferService;
        this.queryService = queryService;
    }

    @PostMapping("/transfer")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "Client-generated key guaranteeing this transfer is applied at most once")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody TransferRequest request) {
        boolean replay = transferService.idempotencyKeyExists(idempotencyKey);
        Transaction transaction = transferService.transfer(
                new TransferCommand(request.sourceAccountId(), request.targetAccountId(), request.amount()),
                idempotencyKey, authorization);
        HttpStatus status = replay ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable UUID id,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                        Authentication authentication) {
        return TransactionResponse.from(queryService.getById(id, authorization, authentication));
    }

    @GetMapping
    public Page<TransactionResponse> getStatement(@RequestParam UUID accountId,
                                                   @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                   Authentication authentication,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return queryService.getStatement(accountId, pageable, authorization, authentication)
                .map(TransactionResponse::from);
    }
}
