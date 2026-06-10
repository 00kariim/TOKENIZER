package com.simulator.mdes.service;

import com.simulator.mdes.exception.ResourceNotFoundException;
import com.simulator.mdes.model.TokenVault;
import com.simulator.mdes.repository.TokenVaultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * De-tokenization service — resolves a payment token (DPAN) back to the
 * opaque {@code panUniqueReference} which the Core Banking service uses to
 * look up the real PAN internally.
 *
 * <p><b>Security contract</b>: This service NEVER exposes the real PAN.
 * It returns only the {@code panUniqueReference} — an opaque, non-guessable
 * identifier that the Core Banking service maps to the real card internally.
 * The de-tokenization result is consumed only by
 * {@link com.simulator.mdes.controller.TransactionController} and forwarded
 * to Core Banking via the Feign client; it is never returned to external callers.
 *
 * <p>Domain Restriction Controls are enforced here — a token may only be
 * de-tokenized if the presentment mode (NFC, ECOM, etc.) is listed in
 * {@code token_vault.domain_restriction}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DetokenizationService {

    private final TokenVaultRepository vaultRepository;

    /**
     * Resolves a DPAN to its {@code panUniqueReference} for Core Banking forwarding.
     *
     * @param tokenValue    the 16-digit payment token (DPAN) from the POS terminal
     * @param presentmentMode the mode being used: {@code NFC}, {@code ECOM}, etc.
     * @return the opaque {@code panUniqueReference} stored in the token vault
     * @throws ResourceNotFoundException   if the token is not found
     * @throws IllegalStateException       if the token is not ACTIVE
     * @throws SecurityException           if the presentment mode violates Domain Restriction Controls
     */
    public String detokenize(String tokenValue, String presentmentMode) {
        TokenVault token = vaultRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenValue));

        // Lifecycle check — suspended / deleted tokens must be rejected here.
        if (!"ACTIVE".equals(token.getStatus())) {
            log.warn("Detokenization denied — token={} is {}", tokenValue, token.getStatus());
            throw new IllegalStateException("Token is not ACTIVE — status: " + token.getStatus());
        }

        // Domain Restriction Control — enforce allowed presentment modes.
        validatePresentmentMode(token, presentmentMode);

        log.debug("Detokenized — token={}...{} → panRef={}",
                tokenValue.substring(0, 4), tokenValue.substring(12),
                token.getPanUniqueReference());

        return token.getPanUniqueReference();
    }

    /**
     * Checks that the requested presentment mode is permitted by the token's
     * {@code domainRestriction} JSONB field.
     *
     * <p>If no domain restriction is configured, all modes are allowed
     * (permissive default for simulator environment).
     */
    @SuppressWarnings("unchecked")
    private void validatePresentmentMode(TokenVault token, String presentmentMode) {
        if (token.getDomainRestriction() == null || token.getDomainRestriction().isEmpty()) {
            return; // No restriction configured — allow all modes.
        }

        Object modesObj = token.getDomainRestriction().get("presentmentModes");
        if (modesObj instanceof java.util.List<?> modes) {
            boolean allowed = modes.stream()
                    .anyMatch(m -> m.toString().equalsIgnoreCase(presentmentMode));
            if (!allowed) {
                log.warn("Domain restriction violation — tokenId={}, requestedMode={}, allowed={}",
                        token.getTokenId(), presentmentMode, modes);
                throw new SecurityException(
                        "Presentment mode '" + presentmentMode + "' is not permitted for this token");
            }
        }
    }
}
