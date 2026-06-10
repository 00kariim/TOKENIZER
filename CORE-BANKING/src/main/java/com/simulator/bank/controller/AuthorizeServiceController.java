package com.simulator.bank.controller;

import com.simulator.bank.model.Card;
import com.simulator.bank.repository.CardRepository;
import com.simulator.bank.service.IdvService;
import com.simulator.bank.service.IdvService.ActivationMethodDto;
import com.simulator.bank.service.LedgerService;
import com.simulator.bank.service.OtpService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the provisioning-related Core Banking endpoints.
 *
 * <p>All endpoints are exclusively called by the MDES TSP Simulator service.
 * They are not reachable from external networks (enforced by Docker networking
 * and the {@link com.simulator.bank.config.SecurityConfig} JWT validation).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/core/authorizeService} — validate card identity, initiate OTP</li>
 *   <li>{@code POST /api/v1/core/deliverActivationCode} — (re-)trigger OTP delivery</li>
 *   <li>{@code POST /api/v1/core/notifyServiceActivated} — confirm token activated</li>
 *   <li>{@code POST /api/v1/core/card/lookup} — pure read: resolve panUniqueReference → card metadata</li>
 *   <li>{@code GET  /api/v1/core/account/{accountId}/balance} — live balance from ledger</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/core")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthorizeServiceController {

    private final IdvService     idvService;
    private final OtpService     otpService;
    private final LedgerService  ledgerService;   // FIX: wired for real balance lookup
    private final CardRepository cardRepository;  // FIX: direct lookup for /card/lookup (no side-effects)

    // ── POST /authorizeService ────────────────────────────────────────────────

    @PostMapping("/authorizeService")
    public ResponseEntity<AuthorizeServiceResponse> authorizeService(
            @Valid @RequestBody AuthorizeServiceRequest request) {

        log.info("authorizeService — tokenRequestorId={}, panRef={}",
                request.authorizeServiceRequest().tokenRequestorId(),
                request.authorizeServiceRequest().panUniqueReference());

        List<ActivationMethodDto> methods = idvService.authorizeService(
                request.authorizeServiceRequest().panUniqueReference(),
                request.authorizeServiceRequest().tokenRequestorId()
        );

        return ResponseEntity.ok(new AuthorizeServiceResponse(
                new AuthorizeServiceResult(
                        UUID.randomUUID().toString(),
                        "REQUIRE_ACTIVATION",
                        methods
                )
        ));
    }

    // ── POST /deliverActivationCode ───────────────────────────────────────────

    @PostMapping("/deliverActivationCode")
    public ResponseEntity<SimpleResult> deliverActivationCode(
            @Valid @RequestBody DeliverRequest request) {

        otpService.deliverActivationCode(
                request.panUniqueReference(),
                request.activationMethodId()
        );
        return ResponseEntity.ok(new SimpleResult("SUCCESS"));
    }

    // ── POST /notifyServiceActivated ──────────────────────────────────────────

    @PostMapping("/notifyServiceActivated")
    public ResponseEntity<SimpleResult> notifyServiceActivated(
            @Valid @RequestBody NotifyRequest request) {

        log.info("Token activated — tokenRef={}, panRef={}, walletId={}",
                request.tokenUniqueReference(),
                request.panUniqueReference(),
                request.walletId());
        return ResponseEntity.ok(new SimpleResult("SUCCESS"));
    }

    // ── POST /card/lookup ─────────────────────────────────────────────────────
    // FIX: was incorrectly calling idvService.authorizeService() which created
    //      a phantom OTP as a side effect. Now does a pure, read-only DB lookup.

    @PostMapping("/card/lookup")
    public ResponseEntity<CardLookupResponse> cardLookup(
            @Valid @RequestBody CardLookupRequest request) {

        Card card = cardRepository.findByPanUniqueReference(request.panUniqueReference())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Card not found for panRef: " + request.panUniqueReference()));

        log.debug("card/lookup — panRef={}, status={}, brand={}",
                request.panUniqueReference(), card.getCardStatus(), card.getCardBrand());

        return ResponseEntity.ok(new CardLookupResponse(
                request.panUniqueReference(),
                card.getCardStatus(),   // ACTIVE / BLOCKED / EXPIRED
                card.getCardBrand(),    // MC / VISA / AMEX
                card.getExpiry(),       // MMYY — no PAN, no CVV
                card.getTokenizationAllowed()
        ));
    }

    // ── GET /account/{accountId}/balance ─────────────────────────────────────
    // FIX: was returning hardcoded BigDecimal.ZERO — now delegates to LedgerService.

    @GetMapping("/account/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        BigDecimal balance = ledgerService.getBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(accountId.toString(), balance));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record AuthorizeServiceRequest(AuthorizeServicePayload authorizeServiceRequest) {}

    public record AuthorizeServicePayload(
            String requestId,
            @NotBlank String tokenRequestorId,
            @NotBlank String panUniqueReference,
            Object fundingAccountInfo,
            Object deviceInfo,
            String walletId,
            String tokenType
    ) {}

    public record AuthorizeServiceResponse(AuthorizeServiceResult authorizeServiceResponse) {}

    public record AuthorizeServiceResult(
            String responseId,
            String decision,
            List<ActivationMethodDto> activationMethods
    ) {}

    public record DeliverRequest(
            @NotBlank String panUniqueReference,
            @NotBlank String activationMethodId
    ) {}

    public record NotifyRequest(
            String tokenUniqueReference,
            String tokenValue,
            @NotBlank String panUniqueReference,
            String walletId
    ) {}

    public record CardLookupRequest(@NotBlank String panUniqueReference) {}

    public record CardLookupResponse(
            String panUniqueReference,
            String cardStatus,
            String cardBrand,
            String expiry,
            Boolean tokenizationAllowed
    ) {}

    public record BalanceResponse(String accountId, BigDecimal balance) {}

    public record SimpleResult(String result) {}
}
