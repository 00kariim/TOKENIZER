package com.simulator.bank.service;

import com.simulator.bank.model.Card;
import com.simulator.bank.model.Otp;
import com.simulator.bank.repository.CardRepository;
import com.simulator.bank.repository.OtpRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ID&V (Identity and Verification) orchestration service.
 *
 * <p>Validates a card identity from the panUniqueReference received from the TSP
 * and determines whether the card is eligible for tokenization. On success, it
 * delegates OTP generation to {@link OtpService} and returns the activation methods
 * available to the cardholder.
 *
 * <p>This implements the {@code authorizeService} EMVCo API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IdvService {

    private final CardRepository cardRepository;
    private final OtpService otpService;

    /**
     * Validates the card identified by {@code panUniqueReference} and initiates
     * the OTP-based identity verification flow.
     *
     * @param panUniqueReference the opaque card reference sent by the TSP
     * @param tokenRequestorId   identifies the wallet (e.g. "GOOGLEPAY_001")
     * @return list of activation method options for the cardholder
     * @throws EntityNotFoundException if no card matches the reference
     * @throws IllegalStateException   if the card is not eligible for tokenization
     */
    @Transactional
    public List<ActivationMethodDto> authorizeService(
            String panUniqueReference, String tokenRequestorId) {

        Card card = cardRepository.findByPanUniqueReference(panUniqueReference)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Card not found for panRef: " + panUniqueReference));

        // Eligibility checks.
        if (!"ACTIVE".equals(card.getCardStatus())) {
            throw new IllegalStateException("Card is not ACTIVE: " + card.getCardStatus());
        }
        if (!Boolean.TRUE.equals(card.getTokenizationAllowed())) {
            throw new IllegalStateException("Tokenization is not permitted for this card");
        }

        // Generate OTP and return delivery method to the TSP.
        String activationMethodId = generateMethodId();
        otpService.generateAndStoreOtp(panUniqueReference, activationMethodId);

        // In a production system the masked phone/email would come from the account profile.
        // For the simulator we use a fixed masked value.
        log.info("ID&V initiated — panRef={}, tokenRequestorId={}, methodId={}",
                panUniqueReference, tokenRequestorId, activationMethodId);

        return List.of(new ActivationMethodDto(
                "TEXT_TO_CARDHOLDER_NUMBER",
                activationMethodId,
                "******1234"   // masked mobile number
        ));
    }

    /** Generates a random 8-char alphanumeric activation method ID. */
    private String generateMethodId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /** DTO returned to the TSP's authorizeService response. */
    public record ActivationMethodDto(
            String activationMethodType,
            String activationMethodId,
            String activationMethodValue
    ) {}
}
