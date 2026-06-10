package com.simulator.bank.controller;

import com.simulator.bank.service.AuthorizationService;
import com.simulator.bank.service.AuthorizationService.AuthResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for payment authorisation.
 *
 * <p>Endpoint: {@code POST /api/v1/core/authorizeTransaction}
 *
 * <p>Called exclusively by the MDES TSP Simulator after validating the
 * token cryptogram and de-tokenizing the DPAN → panUniqueReference.
 * This controller never receives or returns a real PAN.
 */
@RestController
@RequestMapping("/api/v1/core")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TransactionController {

    private final AuthorizationService authorizationService;

    // ── POST /authorizeTransaction ────────────────────────────────────────────

    @PostMapping("/authorizeTransaction")
    public ResponseEntity<AuthorizeTransactionResponse> authorizeTransaction(
            @Valid @RequestBody AuthorizeTransactionRequest request) {

        AuthorizeTransactionPayload p = request.authorizeTransactionRequest();

        log.info("authorizeTransaction — panRef={}..., amount={} {}, merchant={}",
                p.panUniqueReference().substring(0, Math.min(8, p.panUniqueReference().length())),
                p.amount(), p.currency(), p.merchantId());

        AuthResult result = authorizationService.authorizeTransaction(
                p.panUniqueReference(),
                p.amount(),
                p.currency(),
                p.merchantId(),
                p.merchantName(),
                p.posEntryMode()
        );

        return ResponseEntity.ok(new AuthorizeTransactionResponse(
                new AuthorizeTransactionResult(
                        UUID.randomUUID().toString(),
                        result.decision(),
                        result.authorizationCode(),
                        result.availableBalance()
                )
        ));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record AuthorizeTransactionRequest(
            AuthorizeTransactionPayload authorizeTransactionRequest
    ) {}

    public record AuthorizeTransactionPayload(
            String requestId,
            @NotBlank String panUniqueReference,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            String merchantId,
            String merchantName,
            String merchantCategoryCode,
            String posEntryMode
    ) {}

    public record AuthorizeTransactionResponse(
            AuthorizeTransactionResult authorizeTransactionResponse
    ) {}

    public record AuthorizeTransactionResult(
            String responseId,
            String decision,
            String authorizationCode,
            BigDecimal availableBalance
    ) {}
}
