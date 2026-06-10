package com.simulator.bank.service;

import com.simulator.bank.model.Card;
import com.simulator.bank.model.Transaction;
import com.simulator.bank.repository.CardRepository;
import com.simulator.bank.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Payment authorisation engine — the heart of the Core Banking service.
 *
 * <p>Processing steps for each authorisation request:
 * <ol>
 *   <li>Look up the card by {@code panUniqueReference}.</li>
 *   <li>Decrypt the PAN (AES-256-GCM) — briefly, only within this method.</li>
 *   <li>Verify the card is ACTIVE and balance is sufficient.</li>
 *   <li>Apply basic fraud rules (placeholder).</li>
 *   <li>Debit the account via {@link LedgerService}.</li>
 *   <li>Write the transaction record (APPROVED or DECLINED).</li>
 *   <li>Return the authorisation decision + auth code to the TSP.</li>
 * </ol>
 *
 * <p><b>Security:</b> The decrypted PAN is held only as a local variable during
 * step 2–5 and is never stored, logged, or returned.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private static final String AES_GCM    = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS  = 128;
    private static final int GCM_IV_BYTES  = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${pan.encryption.key}")
    private String masterKeyBase64;

    private final CardRepository       cardRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService         ledgerService;

    /**
     * Authorises a payment using the {@code panUniqueReference} forwarded by the TSP.
     *
     * @param panUniqueReference opaque reference to the real card
     * @param amount             transaction amount
     * @param currency           ISO 4217 currency
     * @param merchantId         merchant identifier for the ledger record
     * @param merchantName       merchant display name
     * @param entryMode          presentment mode (NFC, ECOM, MANUAL)
     * @return authorisation result
     */
    @Transactional
    public AuthResult authorizeTransaction(
            String panUniqueReference,
            BigDecimal amount,
            String currency,
            String merchantId,
            String merchantName,
            String entryMode
    ) {
        Card card = cardRepository.findByPanUniqueReference(panUniqueReference)
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + panUniqueReference));

        // ── Card-level checks ────────────────────────────────────────────────
        if (!"ACTIVE".equals(card.getCardStatus())) {
            return decline(panUniqueReference, amount, currency, merchantId, merchantName, entryMode,
                    "CARD_NOT_ACTIVE");
        }

        // ── Balance check via Ledger ─────────────────────────────────────────
        BigDecimal currentBalance = ledgerService.getBalance(card.getAccount().getAccountId());
        if (currentBalance.compareTo(amount) < 0) {
            return decline(panUniqueReference, amount, currency, merchantId, merchantName, entryMode,
                    "INSUFFICIENT_FUNDS");
        }

        // ── Debit account ────────────────────────────────────────────────────
        ledgerService.debit(card.getAccount().getAccountId(), amount);
        BigDecimal newBalance = ledgerService.getBalance(card.getAccount().getAccountId());

        // ── Write APPROVED transaction record ────────────────────────────────
        String authCode = generateAuthCode();
        Transaction tx = Transaction.builder()
                .panUniqueReference(panUniqueReference)
                .amount(amount)
                .currency(currency)
                .merchantId(merchantId)
                .merchantName(merchantName)
                .authCode(authCode)
                .status("APPROVED")
                .entryMode(entryMode)
                .build();
        transactionRepository.save(tx);

        log.info("Transaction APPROVED — panRef={}..., amount={} {}, authCode={}",
                panUniqueReference.substring(0, 8), amount, currency, authCode);

        return new AuthResult("APPROVED", authCode, newBalance, null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuthResult decline(String panRef, BigDecimal amount, String currency,
                                String merchantId, String merchantName, String entryMode,
                                String reason) {
        Transaction tx = Transaction.builder()
                .panUniqueReference(panRef)
                .amount(amount)
                .currency(currency)
                .merchantId(merchantId)
                .merchantName(merchantName)
                .status("DECLINED")
                .declineReason(reason)
                .entryMode(entryMode)
                .build();
        transactionRepository.save(tx);
        log.warn("Transaction DECLINED — panRef={}..., reason={}", panRef.substring(0, 8), reason);
        return new AuthResult("DECLINED", null, null, reason);
    }

    private String generateAuthCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    // ── Encryption utilities (PAN at rest) ───────────────────────────────────

    /**
     * Encrypts a raw PAN for storage in the {@code pan} column.
     *
     * @param rawPan the 16-digit plaintext PAN
     * @return Base64-encoded IV + ciphertext + GCM tag
     */
    public String encryptPan(String rawPan) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(rawPan.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("PAN encryption failed", e);
        }
    }

    private SecretKeySpec masterKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(masterKeyBase64), "AES");
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    public record AuthResult(
            String decision,
            String authorizationCode,
            BigDecimal availableBalance,
            String declineReason
    ) {}
}
