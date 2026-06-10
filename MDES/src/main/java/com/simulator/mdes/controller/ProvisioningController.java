package com.simulator.mdes.controller;

import com.simulator.mdes.service.TokenService;
import com.simulator.mdes.service.TokenService.*;
import com.simulator.mdes.client.CoreBankingClient.ActivationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the token provisioning flow.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/mdes/provisioning/tokenize} — initiate tokenization (step 1)</li>
 *   <li>{@code POST /api/v1/mdes/provisioning/activate} — submit OTP to complete (step 2)</li>
 * </ul>
 *
 * <p>These are the only MDES endpoints exposed externally (via Nginx).
 * Request bodies are validated by Bean Validation before reaching the service layer.
 */
@RestController
@RequestMapping("/api/v1/mdes/provisioning")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ProvisioningController {

    private final TokenService tokenService;

    // ── POST /tokenize ────────────────────────────────────────────────────────

    /**
     * Step 1: Initiates card tokenization.
     *
     * <p>The Flutter SDK sends an RSA-encrypted PAN payload alongside device metadata.
     * The TSP forwards the {@code panUniqueReference} to Core Banking for ID&V and
     * returns the OTP delivery options to the SDK.
     *
     * @param request the tokenization request from Flutter
     * @return 200 OK with decision {@code REQUIRE_ACTIVATION} and activation methods,
     *         or 4xx if validation fails or Core Banking denies the request
     */
    @PostMapping("/tokenize")
    public ResponseEntity<TokenizeResponse> tokenize(@Valid @RequestBody TokenizeRequest request) {
        log.info("Tokenize request — tokenRequestorId={}, walletId={}",
                request.tokenRequestorId(), request.walletId());

        List<ActivationMethod> methods = tokenService.initiateProvisioning(
                new TokenizationRequest(
                        request.tokenRequestorId(),
                        request.panUniqueReference(),
                        request.encryptedData(),
                        request.publicKeyFingerprint(),
                        request.encryptedKey(),
                        request.deviceName(),
                        request.deviceType(),
                        request.deviceId(),
                        request.osVersion(),
                        request.walletId(),
                        request.tokenType()
                )
        );

        return ResponseEntity.ok(new TokenizeResponse(
                UUID.randomUUID().toString(),
                "REQUIRE_ACTIVATION",
                request.panUniqueReference(),
                methods
        ));
    }

    // ── POST /activate ────────────────────────────────────────────────────────

    /**
     * Step 2: Completes provisioning by validating the cardholder OTP.
     *
     * <p>On success, the DPAN is issued, stored in the vault, and returned to
     * the Flutter SDK for secure storage.
     *
     * @param request the activation request containing OTP and activationMethodId
     * @return 200 OK with the issued payment token and its unique reference
     */
    @PostMapping("/activate")
    public ResponseEntity<ActivateResponse> activate(@Valid @RequestBody ActivateRequest request) {
        log.info("Activate request — panRef={}, activationMethodId={}",
                request.panUniqueReference(), request.activationMethodId());

        TokenActivationResult result = tokenService.activateToken(
                new ActivationRequest(
                        request.panUniqueReference(),
                        request.activationMethodId(),
                        request.activationCode(),
                        request.tokenRequestorId(),
                        request.walletId(),
                        request.deviceId(),
                        request.cardBrand(),
                        request.tokenType()
                )
        );

        return ResponseEntity.ok(new ActivateResponse(
                "APPROVED",
                result.paymentToken(),
                result.tokenExpiry(),
                result.tokenUniqueReference(),
                3 // assurance level
        ));
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    public record TokenizeRequest(
            @NotBlank String tokenRequestorId,
            @NotBlank String panUniqueReference,
            @NotBlank String encryptedData,
            String publicKeyFingerprint,
            String encryptedKey,
            String deviceName,
            String deviceType,
            String deviceId,
            String osVersion,
            @NotBlank String walletId,
            String tokenType
    ) {}

    public record TokenizeResponse(
            String requestId,
            String decision,
            String panUniqueReference,
            List<ActivationMethod> activationMethods
    ) {}

    public record ActivateRequest(
            @NotBlank String panUniqueReference,
            @NotBlank String activationMethodId,
            @NotBlank @Size(min = 6, max = 6) String activationCode,
            @NotBlank String tokenRequestorId,
            String walletId,
            String deviceId,
            String cardBrand,
            String tokenType
    ) {}

    public record ActivateResponse(
            String decision,
            String paymentToken,
            String tokenExpiry,
            String tokenUniqueReference,
            int tokenAssuranceLevel
    ) {}
}
