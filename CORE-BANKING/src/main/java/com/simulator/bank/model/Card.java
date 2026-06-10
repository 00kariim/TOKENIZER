package com.simulator.bank.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for the {@code cards} table in {@code saham_core_db}.
 *
 * <p><b>Security constraints:</b>
 * <ul>
 *   <li>{@code pan} — stored AES-256-GCM encrypted; the raw PAN is decrypted only
 *       inside the {@link com.simulator.bank.service.AuthorizationService}.</li>
 *   <li>{@code cvvHash} — bcrypt hash of the CVV. Never returned in any API response.</li>
 *   <li>{@code panUniqueReference} — the only value shared with the TSP. Opaque,
 *       non-reversible without Core Banking access.</li>
 * </ul>
 */
@Entity
@Table(name = "cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Card {

    /**
     * Primary key is the AES-256-GCM-encrypted PAN stored as a hex/Base64 string.
     * The column is named {@code pan} in the DB but contains ciphertext — never plaintext.
     */
    @Id
    @Column(name = "pan", nullable = false, length = 128)
    private String pan;   // encrypted at rest — NOT the raw 16-digit PAN

    /**
     * Opaque reference shared with the TSP (mdes_vault_db).
     * Acts as a logical cross-database bridge. NOT a foreign key constraint.
     */
    @Column(name = "pan_unique_reference", nullable = false, unique = true, length = 64)
    private String panUniqueReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** bcrypt hash of the CVV — never stored or logged as plaintext. */
    @Column(name = "cvv_hash", nullable = false, length = 128)
    private String cvvHash;

    /** Card expiry in MMYY format, e.g. {@code "1230"}. */
    @Column(name = "expiry", nullable = false, length = 4)
    private String expiry;

    /** Card brand: {@code MC}, {@code VISA}, or {@code AMEX}. */
    @Column(name = "card_brand", nullable = false, length = 10)
    private String cardBrand;

    /** Card status: {@code ACTIVE}, {@code BLOCKED}, or {@code EXPIRED}. */
    @Column(name = "card_status", nullable = false, length = 20)
    @Builder.Default
    private String cardStatus = "ACTIVE";

    @Column(name = "tokenization_allowed", nullable = false)
    @Builder.Default
    private Boolean tokenizationAllowed = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private java.time.OffsetDateTime createdAt = java.time.OffsetDateTime.now();
}
