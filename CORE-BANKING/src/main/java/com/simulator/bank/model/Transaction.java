package com.simulator.bank.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code transactions} table in {@code saham_core_db}.
 *
 * <p>Written on every payment authorisation attempt — whether APPROVED or DECLINED.
 * Stores {@code panUniqueReference} (never the real PAN) as the card identifier.
 */
@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tx_id", updatable = false, nullable = false)
    private UUID txId;

    /** Opaque card reference — NOT the real PAN. */
    @Column(name = "pan_unique_reference", nullable = false, length = 64)
    private String panUniqueReference;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "merchant_id", length = 50)
    private String merchantId;

    @Column(name = "merchant_name", length = 120)
    private String merchantName;

    /** 6-character authorisation code — null if declined. */
    @Column(name = "auth_code", length = 10)
    private String authCode;

    /** {@code APPROVED} or {@code DECLINED}. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Human-readable reason for decline — null if approved. */
    @Column(name = "decline_reason", length = 60)
    private String declineReason;

    /** Presentment mode: {@code NFC}, {@code ECOM}, {@code MANUAL}. */
    @Column(name = "entry_mode", length = 30)
    private String entryMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
