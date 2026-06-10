package com.simulator.mdes.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code token_vault} table in {@code mdes_vault_db}.
 *
 * <p>The {@code token_value} (DPAN) is the surrogate PAN issued by the TSP.
 * The real PAN never appears here — only the opaque {@code pan_unique_reference}.
 */
@Entity
@Table(name = "token_vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVault {

    /** Internal surrogate primary key — never exposed externally. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id", updatable = false, nullable = false)
    private UUID tokenId;

    /** Device-specific surrogate PAN (DPAN) — 16-digit string. */
    @Column(name = "token_value", nullable = false, unique = true, length = 16)
    private String tokenValue;

    /**
     * Opaque reference to the real PAN stored in {@code saham_core_db}.
     * This is an application-level foreign key — NOT a DB constraint — to preserve DB isolation.
     */
    @Column(name = "pan_unique_reference", nullable = false, length = 64)
    private String panUniqueReference;

    /** Token expiry in MMYY format, e.g. {@code "1230"}. */
    @Column(name = "token_expiry", nullable = false, length = 4)
    private String tokenExpiry;

    /** Wallet identifier, e.g. {@code "214"} = Google Pay. */
    @Column(name = "wallet_id", nullable = false, length = 20)
    private String walletId;

    /** Token Requestor ID, e.g. {@code "GOOGLEPAY_001"}. */
    @Column(name = "token_requestor_id", nullable = false, length = 30)
    private String tokenRequestorId;

    /** Hardware-bound device fingerprint — null for cloud-based tokens. */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** Token type: {@code DEVICE_SPECIFIC} or {@code CLOUD}. */
    @Column(name = "token_type", nullable = false, length = 20)
    @Builder.Default
    private String tokenType = "DEVICE_SPECIFIC";

    /**
     * Lifecycle status: {@code ACTIVE}, {@code SUSPENDED}, or {@code DELETED}.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** EMVCo assurance level — 0 = lowest, 3 = highest. */
    @Column(name = "assurance_level", nullable = false)
    @Builder.Default
    private Short assuranceLevel = 0;

    /**
     * JSONB column encoding allowed presentment modes
     * (e.g. {@code {"presentmentModes": ["NFC", "ECOM"]}}).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_restriction", columnDefinition = "jsonb")
    private Map<String, Object> domainRestriction;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
