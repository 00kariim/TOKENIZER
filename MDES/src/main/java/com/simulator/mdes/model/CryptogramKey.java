package com.simulator.mdes.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code cryptogram_keys} table in {@code mdes_vault_db}.
 *
 * <p>Holds the per-token AES-128 symmetric key (encrypted at rest) and the
 * Application Transaction Counter (ATC) used for ARQC-style cryptogram computation.
 *
 * <p><b>Security note:</b> {@code symmetricKey} is stored AES-256-GCM-encrypted using
 * the {@code PAN_ENCRYPTION_KEY} environment variable. It is decrypted only within
 * {@link com.simulator.mdes.service.CryptogramService}.
 */
@Entity
@Table(name = "cryptogram_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CryptogramKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "key_id", updatable = false, nullable = false)
    private UUID keyId;

    /**
     * Foreign key to {@link TokenVault#tokenId}. CASCADE DELETE ensures
     * the key is purged when the token is removed from the vault.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false, unique = true)
    private TokenVault token;

    /**
     * AES-128 symmetric key — encrypted at rest (Base64-encoded ciphertext).
     * Never logged, never returned in any API response.
     */
    @Column(name = "symmetric_key", nullable = false, columnDefinition = "TEXT")
    private String symmetricKey;

    /**
     * Monotonically increasing Application Transaction Counter.
     * Incremented atomically on each cryptogram computation to prevent replay.
     */
    @Column(name = "atc", nullable = false)
    @Builder.Default
    private Integer atc = 0;

    /** Timestamp after which this key must be rotated and re-issued. */
    @Column(name = "key_expiry", nullable = false)
    private OffsetDateTime keyExpiry;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * Atomically increments and returns the new ATC value.
     * Callers must persist the updated entity immediately after calling this.
     */
    public int incrementAndGetAtc() {
        return ++this.atc;
    }
}
