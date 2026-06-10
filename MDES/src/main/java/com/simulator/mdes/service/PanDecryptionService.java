package com.simulator.mdes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Decrypts the PAN payload sent by the Flutter SDK.
 *
 * <h3>Expected wire format (JWE-like)</h3>
 * <p>The Flutter SDK encrypts using RSA-OAEP + AES-128-GCM (JWE compact serialization
 * or a custom envelope). The {@code encryptedData} field in the tokenize request body
 * contains a Base64-encoded blob in one of two formats:
 *
 * <ol>
 *   <li><b>Full JWE mode (Flutter with RSA key):</b>
 *       {@code encryptedData = base64(IV[12] + AES-GCM ciphertext)}
 *       {@code encryptedKey  = base64(RSA-OAEP encrypted AES key)}
 *   </li>
 *   <li><b>Simulator passthrough mode (dev/testing without Flutter):</b>
 *       {@code encryptedData = base64({"pan":"...","expiry":"...","cvv":"..."})}
 *       {@code encryptedKey  = null / omitted}
 *   </li>
 * </ol>
 *
 * <p>When {@code encryptedKey} is present and non-blank, full RSA-OAEP decryption
 * is applied. Otherwise the payload is treated as Base64-encoded plaintext JSON
 * (simulator passthrough — logs a warning).
 *
 * <p><b>Security note:</b> The decrypted PAN is held only as a local variable in
 * the calling method and must never be logged, stored, or returned in any API response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PanDecryptionService {

    private static final String AES_GCM    = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LEN = 12;
    private static final int    GCM_TAG    = 128;

    private final RsaKeyService  rsaKeyService;
    private final ObjectMapper   objectMapper;

    /**
     * Decrypts the encrypted PAN payload from the Flutter SDK.
     *
     * @param encryptedData   Base64-encoded ciphertext (IV + AES-GCM) or Base64(JSON) in dev mode
     * @param encryptedKey    Base64-encoded RSA-OAEP encrypted AES key (null in dev mode)
     * @return the decrypted {@link PanPayload} containing pan, expiry, and cvv
     * @throws IllegalArgumentException if decryption fails or the payload is malformed
     */
    public PanPayload decrypt(String encryptedData, String encryptedKey) {
        try {
            String json;
            if (encryptedKey != null && !encryptedKey.isBlank()) {
                // ── Full RSA-OAEP + AES-GCM decryption ──────────────────────
                json = decryptJweEnvelope(encryptedData, encryptedKey);
            } else {
                // ── Simulator passthrough: Base64(plaintext JSON) ────────────
                log.warn("⚠  RSA encryptedKey absent — treating encryptedData as Base64 plaintext JSON (DEV MODE ONLY)");
                json = new String(Base64.getDecoder().decode(encryptedData), StandardCharsets.UTF_8);
            }

            @SuppressWarnings("unchecked")
            Map<String, String> payload = objectMapper.readValue(json, Map.class);

            String pan    = payload.get("pan");
            String expiry = payload.get("expiry");
            String cvv    = payload.get("cvv");

            if (pan == null || pan.isBlank()) {
                throw new IllegalArgumentException("Decrypted PAN payload missing 'pan' field");
            }
            if (pan.length() < 13 || pan.length() > 19) {
                throw new IllegalArgumentException("Decrypted PAN has invalid length: " + pan.length());
            }

            // PAN is intentionally NOT logged — even at DEBUG level.
            log.info("PAN payload decrypted — expiry={}, brand={}", expiry, detectBrand(pan));
            return new PanPayload(pan, expiry != null ? expiry : "", cvv != null ? cvv : "");

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt PAN payload: " + e.getMessage(), e);
        }
    }

    // ── RSA-OAEP + AES-GCM decryption ────────────────────────────────────────

    private String decryptJweEnvelope(String encryptedData, String encryptedKeyB64) throws Exception {
        // 1. RSA-OAEP decrypt the content encryption key (CEK).
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedKeyB64);
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaKeyService.getPrivateKey());
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);

        // 2. AES-128-GCM decrypt the PAN JSON.
        byte[] ivAndCiphertext = Base64.getDecoder().decode(encryptedData);
        byte[] iv         = Arrays.copyOfRange(ivAndCiphertext, 0, GCM_IV_LEN);
        byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, GCM_IV_LEN, ivAndCiphertext.length);

        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        Cipher aesCipher = Cipher.getInstance(AES_GCM);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG, iv));
        byte[] plaintext = aesCipher.doFinal(ciphertext);

        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static String detectBrand(String pan) {
        char first = pan.charAt(0);
        return switch (first) {
            case '4' -> "VISA";
            case '5' -> "MC";
            case '3' -> "AMEX";
            default  -> "UNKNOWN";
        };
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    /**
     * Decrypted PAN payload — held transiently and never persisted or logged.
     *
     * @param pan    the 13–19 digit Primary Account Number
     * @param expiry MMYY format expiry date
     * @param cvv    CVV / CVC code
     */
    public record PanPayload(String pan, String expiry, String cvv) {}
}
