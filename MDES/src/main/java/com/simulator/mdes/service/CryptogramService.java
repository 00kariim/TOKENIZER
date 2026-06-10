package com.simulator.mdes.service;

import com.simulator.mdes.exception.InvalidCryptogramException;
import com.simulator.mdes.exception.ResourceNotFoundException;
import com.simulator.mdes.model.CryptogramKey;
import com.simulator.mdes.model.TokenVault;
import com.simulator.mdes.repository.CryptogramKeyRepository;
import com.simulator.mdes.repository.TokenVaultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * ARQC-style cryptogram computation and validation engine.
 *
 * <p>Algorithm (section 6.3 of design doc):
 * data = tokenValue + amount + currency + merchantId + ATC(hex4)
 * cryptogram = HMAC-SHA256(K, data)[0..8] → 16 uppercase hex chars
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CryptogramService {

    private static final String HMAC_SHA256    = "HmacSHA256";
    private static final String AES_GCM        = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS   = 128;
    private static final int    GCM_IV_BYTES   = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${pan.encryption.key}")
    private String masterEncryptionKeyBase64;

    private final CryptogramKeyRepository keyRepository;
    private final TokenVaultRepository    vaultRepository;

    // ── Generate ─────────────────────────────────────────────────────────────

    @Transactional
    public String generateCryptogram(String tokenValue, BigDecimal amount,
                                     String currency, String merchantId) {
        TokenVault token = vaultRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenValue));

        CryptogramKey keyRecord = keyRepository.findByTokenIdForUpdate(token.getTokenId())
                .orElseThrow(() -> new ResourceNotFoundException("Key not found for token: " + tokenValue));

        int atc = keyRecord.incrementAndGetAtc();
        keyRepository.save(keyRecord);

        byte[] rawKey = decryptKeyAtRest(keyRecord.getSymmetricKey());
        String cryptogram = toHmacHex16(rawKey, buildInput(tokenValue, amount, currency, merchantId, atc));

        log.debug("Cryptogram generated — token={}...{}, atc={}", tokenValue.substring(0, 4), tokenValue.substring(12), atc);
        return cryptogram;
    }

    // ── Validate ─────────────────────────────────────────────────────────────

    @Transactional
    public void validateCryptogram(String tokenValue, String presented,
                                   BigDecimal amount, String currency,
                                   String merchantId, String presentedAtcHex) {
        TokenVault token = vaultRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new InvalidCryptogramException("Unknown token"));

        if (!"ACTIVE".equals(token.getStatus())) {
            throw new InvalidCryptogramException("Token not ACTIVE: " + token.getStatus());
        }

        CryptogramKey keyRecord = keyRepository.findByTokenIdForUpdate(token.getTokenId())
                .orElseThrow(() -> new InvalidCryptogramException("Key record missing"));

        int presentedAtc = Integer.parseInt(presentedAtcHex, 16);
        if (presentedAtc <= keyRecord.getAtc()) {
            throw new InvalidCryptogramException("Cryptogram replay — ATC not advancing");
        }

        keyRecord.setAtc(presentedAtc);
        keyRepository.save(keyRecord);

        byte[] rawKey  = decryptKeyAtRest(keyRecord.getSymmetricKey());
        String expected = toHmacHex16(rawKey, buildInput(tokenValue, amount, currency, merchantId, presentedAtc));

        if (!constantTimeEquals(expected, presented.toUpperCase())) {
            throw new InvalidCryptogramException("Cryptogram HMAC mismatch");
        }
    }

    // ── Key-at-rest AES-256-GCM ───────────────────────────────────────────

    public String encryptKeyAtRest(byte[] rawKey) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(rawKey);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Key encryption failed", e);
        }
    }

    byte[] decryptKeyAtRest(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ct = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Key decryption failed", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String buildInput(String tokenValue, BigDecimal amount,
                              String currency, String merchantId, int atc) {
        return tokenValue + amount.toPlainString() + currency + merchantId + String.format("%04X", atc);
    }

    private String toHmacHex16(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac).substring(0, 16).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private SecretKey masterKey() {
        byte[] bytes = Base64.getDecoder().decode(masterEncryptionKeyBase64);
        return new SecretKeySpec(bytes, "AES");
    }
}
