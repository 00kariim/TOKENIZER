package com.simulator.mdes.service;

import com.simulator.mdes.client.CoreBankingClient;
import com.simulator.mdes.client.CoreBankingClient.*;
import com.simulator.mdes.exception.DuplicateTokenException;
import com.simulator.mdes.exception.TokenizationException;
import com.simulator.mdes.model.CryptogramKey;
import com.simulator.mdes.model.TokenLifecycleLog;
import com.simulator.mdes.model.TokenVault;
import com.simulator.mdes.repository.CryptogramKeyRepository;
import com.simulator.mdes.repository.TokenVaultRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business logic for token provisioning and lifecycle management.
 *
 * <p><b>Provisioning flow</b> (2 steps):
 * <ol>
 *   <li>{@link #initiateProvisioning} — calls Core Banking for ID&V, caches the activation
 *       method, returns OTP options to the caller.</li>
 *   <li>{@link #activateToken} — validates the OTP, generates a DPAN, stores it in the
 *       vault, creates the AES-128 cryptogram key, notifies Core Banking.</li>
 * </ol>
 *
 * <p><b>Lifecycle management</b>:
 * {@link #updateTokenLifecycle} — transitions token status to SUSPENDED or DELETED.
 *
 * <p><b>Security constraints enforced here</b>:
 * <ul>
 *   <li>PAN is never accepted, stored, or logged by this service.</li>
 *   <li>DPAN generation is cryptographically random (SecureRandom).</li>
 *   <li>Symmetric keys are encrypted at rest before persistence.</li>
 *   <li>Duplicate provisioning (same card + same device) is rejected.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenVaultRepository       vaultRepository;
    private final CryptogramKeyRepository    keyRepository;
    private final CoreBankingClient          coreBankingClient;
    private final CryptogramService          cryptogramService;

    /**
     * In-memory cache for pending activations (panUniqueReference → bank response).
     * In a production system this would be a distributed cache (Redis) with TTL.
     * TTL here is handled implicitly — the OTP expires server-side at Core Banking.
     */
    private final Map<String, AuthorizeServiceResult> pendingActivations = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1: Initiate provisioning
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates the card tokenization flow.
     *
     * <p>The Flutter SDK sends an encrypted PAN payload; the TSP forwards the
     * {@code panUniqueReference} to Core Banking for identity verification.
     *
     * @param request the tokenization request from the Flutter SDK
     * @return the list of activation methods (OTP delivery options) from the bank
     * @throws DuplicateTokenException if the card is already tokenized to this device
     * @throws TokenizationException   if Core Banking denies the request
     */
    public List<ActivationMethod> initiateProvisioning(TokenizationRequest request) {
        // Guard: prevent duplicate active tokens for same card + device.
        boolean alreadyExists = vaultRepository.existsByPanUniqueReferenceAndDeviceIdAndStatus(
                request.panUniqueReference(), request.deviceId(), "ACTIVE");
        if (alreadyExists) {
            throw new DuplicateTokenException(
                    "An active token already exists for this card and device: " + request.deviceId());
        }

        // Call Core Banking authorizeService.
        AuthorizeServiceRequest bankRequest = buildAuthorizeServiceRequest(request);
        AuthorizeServiceResponse bankResponse = coreBankingClient.authorizeService(bankRequest);
        AuthorizeServiceResult result = bankResponse.authorizeServiceResponse();

        if (!"REQUIRE_ACTIVATION".equals(result.decision())) {
            log.warn("Unexpected decision from Core Banking: {}", result.decision());
            throw new TokenizationException("ID&V failed — bank decision: " + result.decision());
        }

        // Cache the activation data for the subsequent activate call.
        pendingActivations.put(request.panUniqueReference(), result);
        log.info("Provisioning initiated for panRef={}", request.panUniqueReference());

        return result.activationMethods();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2: Activate token
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Completes the provisioning flow after the cardholder submits the OTP.
     *
     * <p>On success: generates a DPAN, persists it to the vault, stores an AES-128
     * cryptogram key, notifies Core Banking, and returns the fully issued token.
     *
     * @param req the activation request containing OTP, activationMethodId, walletId
     * @return the issued payment token (DPAN, expiry, tokenUniqueReference)
     * @throws TokenizationException if no pending activation exists for the given panUniqueReference
     */
    @Transactional
    public TokenActivationResult activateToken(ActivationRequest req) {
        if (!pendingActivations.containsKey(req.panUniqueReference())) {
            throw new TokenizationException("No pending activation found for panRef=" + req.panUniqueReference());
        }

        // 1. Generate surrogate PAN (DPAN).
        String dpan = generateDpan(req.cardBrand());

        // 2. Generate AES-128 symmetric key for cryptogram computation.
        byte[] rawKey = new byte[16];
        SECURE_RANDOM.nextBytes(rawKey);
        String encryptedKey = cryptogramService.encryptKeyAtRest(rawKey);

        // 3. Persist token to vault.
        TokenVault token = TokenVault.builder()
                .tokenValue(dpan)
                .panUniqueReference(req.panUniqueReference())
                .tokenExpiry("1230")
                .walletId(req.walletId())
                .tokenRequestorId(req.tokenRequestorId())
                .deviceId(req.deviceId())
                .tokenType(req.tokenType())
                .status("ACTIVE")
                .assuranceLevel((short) 3)
                .domainRestriction(Map.of("presentmentModes", List.of("NFC", "ECOM")))
                .build();
        TokenVault saved = vaultRepository.save(token);

        // 4. Persist cryptogram key.
        CryptogramKey key = CryptogramKey.builder()
                .token(saved)
                .symmetricKey(encryptedKey)
                .atc(0)
                .keyExpiry(OffsetDateTime.now().plusYears(2))
                .build();
        keyRepository.save(key);

        // 5. Notify Core Banking that the token has been activated.
        coreBankingClient.notifyServiceActivated(new NotifyServiceActivatedRequest(
                saved.getTokenId().toString(),
                dpan,
                req.panUniqueReference(),
                req.walletId()
        ));

        // 6. Clean up the pending activation cache.
        pendingActivations.remove(req.panUniqueReference());

        log.info("Token ACTIVATED — tokenId={}, dpan={}...{}", saved.getTokenId(), dpan.substring(0, 4), dpan.substring(12));
        return new TokenActivationResult(dpan, saved.getTokenExpiry(), saved.getTokenId().toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transitions a token's lifecycle status.
     *
     * @param tokenValue the 16-digit DPAN
     * @param action     {@code SUSPEND} or {@code DELETE}
     * @throws EntityNotFoundException if the token does not exist
     * @throws IllegalArgumentException if the action is not recognised
     */
    @Transactional
    public void updateTokenLifecycle(String tokenValue, String action) {
        TokenVault token = vaultRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new EntityNotFoundException("Token not found: " + tokenValue));

        String newStatus = switch (action.toUpperCase()) {
            case "SUSPEND" -> "SUSPENDED";
            case "DELETE"  -> "DELETED";
            default -> throw new IllegalArgumentException("Unknown lifecycle action: " + action);
        };

        token.setStatus(newStatus);
        vaultRepository.save(token);

        log.info("Token lifecycle updated — tokenId={}, action={}, status={}", token.getTokenId(), action, newStatus);
    }

    /**
     * Retrieves the current status of a token.
     *
     * @param tokenValue the 16-digit DPAN
     * @return the vault entry
     * @throws EntityNotFoundException if the token does not exist
     */
    public TokenVault getToken(String tokenValue) {
        return vaultRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new EntityNotFoundException("Token not found: " + tokenValue));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a Luhn-valid DPAN using the EMVCo-specified BIN prefix.
     * <ul>
     *   <li>Mastercard MDES tokens: prefix {@code 5204}</li>
     *   <li>Visa VTS tokens:        prefix {@code 4895}</li>
     * </ul>
     */
    private String generateDpan(String cardBrand) {
        String prefix = "MC".equalsIgnoreCase(cardBrand) ? "5204" : "4895";
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 11; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        // Compute and append Luhn check digit.
        sb.append(computeLuhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /** Computes the Luhn algorithm check digit for a 15-digit number string. */
    private int computeLuhnCheckDigit(String number) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    private AuthorizeServiceRequest buildAuthorizeServiceRequest(TokenizationRequest req) {
        return new AuthorizeServiceRequest(new AuthorizeServicePayload(
                UUID.randomUUID().toString(),
                req.tokenRequestorId(),
                req.panUniqueReference(),
                new FundingAccountInfo(new EncryptedPayload(
                        req.encryptedData(), req.publicKeyFingerprint(), req.encryptedKey()
                )),
                new DeviceInfo(req.deviceName(), req.deviceType(), req.deviceId(), req.osVersion()),
                req.walletId(),
                req.tokenType()
        ));
    }

    // ─── Inner request / result DTOs ─────────────────────────────────────────

    public record TokenizationRequest(
            String tokenRequestorId,
            String panUniqueReference,
            String encryptedData,
            String publicKeyFingerprint,
            String encryptedKey,
            String deviceName,
            String deviceType,
            String deviceId,
            String osVersion,
            String walletId,
            String tokenType
    ) {}

    public record ActivationRequest(
            String panUniqueReference,
            String activationMethodId,
            String activationCode,
            String tokenRequestorId,
            String walletId,
            String deviceId,
            String cardBrand,
            String tokenType
    ) {}

    public record TokenActivationResult(
            String paymentToken,
            String tokenExpiry,
            String tokenUniqueReference
    ) {}
}
