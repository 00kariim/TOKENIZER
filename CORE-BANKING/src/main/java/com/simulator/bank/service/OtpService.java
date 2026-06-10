package com.simulator.bank.service;

import com.simulator.bank.model.Otp;
import com.simulator.bank.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * OTP generation, storage, delivery (stub), and verification service.
 *
 * <p><b>Security constraints:</b>
 * <ul>
 *   <li>OTP codes are generated via {@link SecureRandom} — never {@code Math.random()}.</li>
 *   <li>Only the bcrypt hash is stored — the plaintext is logged to console (simulator only)
 *       and discarded immediately after.</li>
 *   <li>Lockout after 3 failed attempts.</li>
 *   <li>10-minute TTL enforced at verification time.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS    = 3;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    private final OtpRepository otpRepository;

    // ── Generate & store ──────────────────────────────────────────────────────

    /**
     * Generates a 6-digit OTP, bcrypt-hashes it, and persists it.
     * The plaintext is logged to the console (simulator behaviour).
     * In production, this would instead call an SMS gateway.
     *
     * @param panUniqueReference the card reference
     * @param activationMethodId the unique method ID linking this OTP to the delivery method
     */
    @Transactional
    public void generateAndStoreOtp(String panUniqueReference, String activationMethodId) {
        String plainOtp = generateSixDigitOtp();

        Otp otp = Otp.builder()
                .panUniqueReference(panUniqueReference)
                .activationMethodId(activationMethodId)
                .codeHash(BCRYPT.encode(plainOtp))
                .deliveryChannel("SMS")
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();

        otpRepository.save(otp);

        // ── SIMULATOR STUB: log OTP to console instead of sending SMS ──
        log.info("╔══════════════════════════════════════╗");
        log.info("║  OTP DELIVERY (SIMULATOR STUB)       ║");
        log.info("║  Code:    {}                      ║", plainOtp);
        log.info("║  Method:  {}               ║", activationMethodId);
        log.info("║  Expires: {} mins             ║", OTP_TTL_MINUTES);
        log.info("╚══════════════════════════════════════╝");
    }

    // ── Deliver ───────────────────────────────────────────────────────────────

    /**
     * Stub: in a real system this would re-trigger the OTP SMS/email.
     * Here it just logs that the delivery was requested.
     *
     * @param panUniqueReference the card reference
     * @param activationMethodId the method to re-send on
     */
    public void deliverActivationCode(String panUniqueReference, String activationMethodId) {
        log.info("OTP re-delivery requested — panRef={}, methodId={}",
                panUniqueReference, activationMethodId);
        // In production: trigger SMS gateway call here.
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Verifies a submitted OTP code against the stored bcrypt hash.
     *
     * @param panUniqueReference the card reference
     * @param activationMethodId the method ID from the provisioning flow
     * @param submittedCode      the 6-digit code entered by the cardholder
     * @throws IllegalStateException if the OTP is expired, locked, or incorrect
     */
    @Transactional
    public void verifyOtp(String panUniqueReference, String activationMethodId, String submittedCode) {
        Otp otp = otpRepository
                .findTopByPanUniqueReferenceAndActivationMethodIdAndUsedFalseOrderByCreatedAtDesc(
                        panUniqueReference, activationMethodId)
                .orElseThrow(() -> new IllegalStateException("No active OTP found for this activation method"));

        if (otp.isExpiredOrLocked()) {
            throw new IllegalStateException("OTP has expired or been locked due to too many attempts");
        }

        if (!BCRYPT.matches(submittedCode, otp.getCodeHash())) {
            otp.setAttempts((short) (otp.getAttempts() + 1));
            otpRepository.save(otp);
            int remaining = MAX_ATTEMPTS - otp.getAttempts();
            throw new IllegalStateException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Mark as used — prevents replay.
        otp.setUsed(true);
        otpRepository.save(otp);
        log.info("OTP verified successfully — panRef={}, methodId={}", panUniqueReference, activationMethodId);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String generateSixDigitOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
