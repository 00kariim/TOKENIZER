-- ─────────────────────────────────────────────────────────────────────────────
-- Flyway migration V1 — Initial schema for mdes_vault_db
-- Applied automatically on service startup via spring.flyway.enabled=true
--
-- Tables created:
--   token_vault          — DPAN (surrogate PAN) storage
--   cryptogram_keys      — Per-token AES-128 symmetric keys + ATC
--   token_lifecycle_log  — Immutable audit log of token state transitions
-- ─────────────────────────────────────────────────────────────────────────────

-- Enable UUID generation extension.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── token_vault ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS token_vault (
    token_id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_value          VARCHAR(16)  NOT NULL UNIQUE,
    pan_unique_reference VARCHAR(64)  NOT NULL,
    token_expiry         VARCHAR(4)   NOT NULL,
    wallet_id            VARCHAR(20)  NOT NULL,
    token_requestor_id   VARCHAR(30)  NOT NULL,
    device_id            VARCHAR(128),
    token_type           VARCHAR(20)  NOT NULL DEFAULT 'DEVICE_SPECIFIC',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    assurance_level      SMALLINT     NOT NULL DEFAULT 0,
    domain_restriction   JSONB,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_token_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_token_type   CHECK (token_type IN ('DEVICE_SPECIFIC', 'CLOUD'))
);

CREATE INDEX idx_token_vault_pan_ref ON token_vault (pan_unique_reference);
CREATE INDEX idx_token_vault_status  ON token_vault (status);

-- ─── cryptogram_keys ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cryptogram_keys (
    key_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id       UUID         NOT NULL UNIQUE
                                REFERENCES token_vault(token_id) ON DELETE CASCADE,
    symmetric_key  TEXT         NOT NULL,
    atc            INTEGER      NOT NULL DEFAULT 0,
    key_expiry     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_atc_non_negative CHECK (atc >= 0)
);

-- ─── token_lifecycle_log ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS token_lifecycle_log (
    log_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id        UUID         NOT NULL,   -- Logical ref only — no FK constraint (intentional)
    event_type      VARCHAR(40)  NOT NULL,
    actor           VARCHAR(60),
    payload_summary TEXT,                    -- MUST NOT contain PAN data
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_event_type CHECK (event_type IN (
        'ISSUED', 'ACTIVATED', 'SUSPENDED', 'DELETED', 'TX_APPROVED', 'TX_DECLINED'
    ))
);

CREATE INDEX idx_lifecycle_log_token ON token_lifecycle_log (token_id);
CREATE INDEX idx_lifecycle_log_event ON token_lifecycle_log (event_type);
