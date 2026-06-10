package com.simulator.mdes.controller;

import com.simulator.mdes.client.CoreBankingClient;
import com.simulator.mdes.client.CoreBankingClient.*;
import com.simulator.mdes.model.TokenVault;
import com.simulator.mdes.service.CryptogramService;
import com.simulator.mdes.service.DetokenizationService;
import com.simulator.mdes.service.TokenService;
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
 * REST controller for transaction authorisation and token lifecycle management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/mdes/transaction/authorize} — merchant payment authorisation</li>
 *   <li>{@code GET  /api/v1/mdes/token/{tokenValue}/status} — query token status</li>
 *   <li>{@code PUT  /api/v1/mdes/token/{tokenValue}/lifecycle} — suspend or delete a token</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/mdes")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TransactionController {

    private final CryptogramService    cryptogramService;
    private final DetokenizationService detokenizationService;
    private final TokenService         tokenService;
    private final CoreBankingClient    coreBankingClient;

    // ── POST /transaction/authorize ───────────────────────────────────────────

    /**
     * Authorises a payment transaction from a POS terminal or e-commerce merchant.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Validate the token cryptogram (HMAC + ATC replay check).</li>
     *   <li>De-tokenize: resolve DPAN → panUniqueReference.</li>
     *   <li>Forward to Core Banking for balance check and ledger debit.</li>
     *   <li>Return authorisation decision + auth code to the merchant.</li>
     * </ol>
     */
    @PostMapping("/transaction/authorize")
    public ResponseEntity<AuthorizeResponse> authorize(@Valid @RequestBody AuthorizeRequest request) {
        log.info("Transaction authorize — merchantId={}, amount={} {}",
                request.merchantId(), request.amount(), request.currency());

        // 1. Validate cryptogram.
        cryptogramService.validateCryptogram(
                request.paymentToken(),
                request.tokenCryptogram(),
                request.amount(),
                request.currency(),
                request.merchantId(),
                request.atc()
        );

        // 2. De-tokenize: DPAN → panUniqueReference (Domain Restriction enforced here).
        String panRef = detokenizationService.detokenize(
                request.paymentToken(), derivePresentmentMode(request.posEntryMode()));

        // 3. Forward to Core Banking for authorization.
        AuthorizeTransactionResponse bankResponse = coreBankingClient.authorizeTransaction(
                new AuthorizeTransactionRequest(new AuthorizeTransactionPayload(
                        UUID.randomUUID().toString(),
                        panRef,
                        request.amount(),
                        request.currency(),
                        request.merchantId(),
                        request.merchantName(),
                        request.merchantCategoryCode(),
                        request.posEntryMode()
                ))
        );

        AuthorizeTransactionResult result = bankResponse.authorizeTransactionResponse();
        log.info("Transaction {} — authCode={}", result.decision(), result.authorizationCode());

        return ResponseEntity.ok(new AuthorizeResponse(
                "tx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                result.decision(),
                result.authorizationCode(),
                panRef,
                "00"
        ));
    }

    // ── GET /token/{tokenValue}/status ────────────────────────────────────────

    /**
     * Returns the current lifecycle status of a token.
     *
     * @param tokenValue the 16-digit DPAN
     * @return token status: {@code ACTIVE}, {@code SUSPENDED}, or {@code DELETED}
     */
    @GetMapping("/token/{tokenValue}/status")
    public ResponseEntity<TokenStatusResponse> getTokenStatus(@PathVariable String tokenValue) {
        TokenVault token = tokenService.getToken(tokenValue);
        return ResponseEntity.ok(new TokenStatusResponse(
                token.getTokenId().toString(),
                token.getTokenValue(),
                token.getStatus(),
                token.getUpdatedAt().toString()
        ));
    }

    // ── PUT /token/{tokenValue}/lifecycle ─────────────────────────────────────

    /**
     * Updates the lifecycle state of a token to {@code SUSPENDED} or {@code DELETED}.
     *
     * @param tokenValue the 16-digit DPAN
     * @param request    contains the action: {@code SUSPEND} or {@code DELETE}
     */
    @PutMapping("/token/{tokenValue}/lifecycle")
    public ResponseEntity<Void> updateLifecycle(
            @PathVariable String tokenValue,
            @Valid @RequestBody LifecycleRequest request) {
        log.info("Lifecycle update — token={}, action={}", tokenValue, request.action());
        tokenService.updateTokenLifecycle(tokenValue, request.action());
        return ResponseEntity.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Maps ISO 8583 POS Entry Mode codes to Domain Restriction presentment mode strings. */
    private String derivePresentmentMode(String posEntryMode) {
        return switch (posEntryMode) {
            case "07" -> "NFC";
            case "81" -> "NFC";
            case "01" -> "MANUAL";
            default   -> "ECOM";
        };
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    public record AuthorizeRequest(
            @NotBlank String paymentToken,
            @NotBlank String tokenCryptogram,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String merchantId,
            String merchantName,
            String merchantCategoryCode,
            String posEntryMode,
            @NotBlank String atc
    ) {}

    public record AuthorizeResponse(
            String transactionId,
            String status,
            String authorizationCode,
            String par,
            String responseCode
    ) {}

    public record TokenStatusResponse(
            String tokenId,
            String tokenValue,
            String status,
            String updatedAt
    ) {}

    public record LifecycleRequest(
            @NotBlank String action
    ) {}
}
