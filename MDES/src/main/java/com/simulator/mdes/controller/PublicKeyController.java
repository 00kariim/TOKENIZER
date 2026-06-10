package com.simulator.mdes.controller;

import com.simulator.mdes.service.RsaKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Exposes the TSP's RSA-2048 public key so the Flutter SDK can retrieve it
 * before encrypting the PAN payload.
 *
 * <p>This endpoint is intentionally <b>unauthenticated</b> — public keys are
 * designed to be publicly distributed. The Flutter SDK calls this once on
 * startup and caches the key.
 *
 * <p>Endpoint: {@code GET /api/v1/mdes/publicKey}
 */
@RestController
@RequestMapping("/api/v1/mdes")
@RequiredArgsConstructor
@Tag(name = "TSP Public Key", description = "RSA public key distribution for Flutter PAN encryption")
public class PublicKeyController {

    private final RsaKeyService rsaKeyService;

    /**
     * Returns the TSP's RSA-2048 public key in two formats:
     * <ul>
     *   <li>{@code pem} — PEM-encoded string for direct use with OpenSSL / PointyCastle</li>
     *   <li>{@code derBase64} — Base64-encoded DER bytes (for Android / iOS crypto APIs)</li>
     * </ul>
     *
     * <p>No authentication required — public keys are safe to distribute openly.
     */
    @GetMapping("/publicKey")
    @Operation(
        summary = "Get TSP RSA public key",
        description = "Returns the RSA-2048 public key in PEM and Base64-DER formats. " +
                      "The Flutter SDK uses this key to RSA-OAEP encrypt the PAN payload " +
                      "before calling POST /provisioning/tokenize."
    )
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        byte[] encoded  = rsaKeyService.getPublicKey().getEncoded();
        String pem      = rsaKeyService.getPublicKeyPem();
        String derB64   = Base64.getEncoder().encodeToString(encoded);

        return ResponseEntity.ok(new PublicKeyResponse("RSA", 2048, pem, derB64));
    }

    public record PublicKeyResponse(
            String algorithm,
            int keySize,
            String pem,
            String derBase64
    ) {}
}
