package com.simulator.mdes.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages the TSP's RSA-2048 key pair used to receive encrypted PAN payloads
 * from the Flutter SDK.
 *
 * <h3>Startup behaviour</h3>
 * <ol>
 *   <li>If key files exist at the configured paths, load them.</li>
 *   <li>If files are absent (first run / dev mode), generate a new RSA-2048
 *       key pair, persist it as PEM files, and log a warning.</li>
 * </ol>
 *
 * <p>The public key is exposed via {@link com.simulator.mdes.controller.PublicKeyController}
 * so the Flutter SDK can retrieve it before encrypting the PAN.
 *
 * <p><b>Security note:</b> The private key file must be readable only by the
 * JVM process (mode 600 in Unix). Mount it as a Docker secret in production.
 */
@Service
@Slf4j
public class RsaKeyService {

    @Value("${tsp.rsa.private-key-path:/app/certs/tsp_private.pem}")
    private String privateKeyPath;

    @Value("${tsp.rsa.public-key-path:/app/certs/tsp_public.pem}")
    private String publicKeyPath;

    @Getter private PrivateKey privateKey;
    @Getter private PublicKey  publicKey;

    /** PEM-encoded public key returned to Flutter via the public key endpoint. */
    @Getter private String publicKeyPem;

    @PostConstruct
    public void init() {
        Path privPath = Path.of(privateKeyPath);
        Path pubPath  = Path.of(publicKeyPath);

        if (Files.exists(privPath) && Files.exists(pubPath)) {
            loadKeysFromFiles(privPath, pubPath);
            log.info("RSA key pair loaded from disk: {}", pubPath);
        } else {
            log.warn("RSA key files not found at configured paths — generating a fresh pair for this session.");
            log.warn("Private key: {}  |  Public key: {}", privateKeyPath, publicKeyPath);
            generateAndPersistKeyPair(privPath, pubPath);
        }

        publicKeyPem = buildPublicKeyPem(publicKey.getEncoded());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void loadKeysFromFiles(Path privPath, Path pubPath) {
        try {
            byte[] privBytes = decodePem(Files.readString(privPath));
            byte[] pubBytes  = decodePem(Files.readString(pubPath));

            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            publicKey  = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair from files", e);
        }
    }

    private void generateAndPersistKeyPair(Path privPath, Path pubPath) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair pair = gen.generateKeyPair();

            privateKey = pair.getPrivate();
            publicKey  = pair.getPublic();

            // Persist to configured paths (best-effort — may fail if container FS is read-only).
            try {
                Files.createDirectories(privPath.getParent());
                Files.writeString(privPath, buildPrivateKeyPem(privateKey.getEncoded()));
                Files.writeString(pubPath,  buildPublicKeyPem(publicKey.getEncoded()));
                log.info("RSA key pair generated and saved: {}", pubPath.getParent());
            } catch (IOException e) {
                log.warn("Could not persist RSA keys to disk (read-only FS?): {}", e.getMessage());
                log.warn("Keys exist in memory only — will be regenerated on next restart.");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    private static String buildPrivateKeyPem(byte[] encoded) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static String buildPublicKeyPem(byte[] encoded) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static byte[] decodePem(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN.*?-----", "")
                .replaceAll("-----END.*?-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }
}
