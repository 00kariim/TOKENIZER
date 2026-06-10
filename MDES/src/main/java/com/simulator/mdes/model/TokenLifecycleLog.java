package com.simulator.mdes.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit log for every state change on a token.
 *
 * <p>Recorded events include: {@code ISSUED}, {@code ACTIVATED}, {@code SUSPENDED},
 * {@code DELETED}, {@code TX_APPROVED}, {@code TX_DECLINED}.
 *
 * <p><b>Security constraint:</b> {@code payloadSummary} must NEVER contain real PAN data.
 * Store only non-sensitive identifiers such as masked PAN (last 4), amounts, or merchant IDs.
 */
@Entity
@Table(name = "token_lifecycle_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenLifecycleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id", updatable = false, nullable = false)
    private UUID logId;

    /** Reference to the token that was affected. Not a DB FK — intentional. */
    @Column(name = "token_id", nullable = false)
    private UUID tokenId;

    /**
     * The event that occurred:
     * {@code ISSUED | ACTIVATED | SUSPENDED | DELETED | TX_APPROVED | TX_DECLINED}.
     */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    /** Who triggered the event — wallet ID, merchant ID, or system actor. */
    @Column(name = "actor", length = 60)
    private String actor;

    /**
     * NON-SENSITIVE summary of the event payload.
     * Example: {@code "amount=42.00 USD, merchant=MCH-PARIS-001, authCode=583920"}.
     */
    @Column(name = "payload_summary", columnDefinition = "TEXT")
    private String payloadSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
