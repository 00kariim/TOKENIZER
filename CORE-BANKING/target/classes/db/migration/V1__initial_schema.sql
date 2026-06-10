-- ─────────────────────────────────────────────────────────────────────────────
-- Flyway V1 — Initial schema for saham_core_db (Core Banking Database)
--
-- Tables: accounts, cards, otps, transactions
--
-- Security constraints enforced here:
--   - cards.pan is the ENCRYPTED PAN (ciphertext), not plaintext
--   - cards.cvv_hash is bcrypt hash only
--   - transactions never store real PAN — only pan_unique_reference
-- ─────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── accounts ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    account_id     UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID           NOT NULL,
    account_number VARCHAR(20)    NOT NULL UNIQUE,
    balance        NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'USD',
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- ─── cards ───────────────────────────────────────────────────────────────────
-- pan column stores AES-256-GCM ciphertext (Base64), NOT the plaintext PAN.
CREATE TABLE IF NOT EXISTS cards (
    pan                  VARCHAR(256)  PRIMARY KEY,     -- encrypted ciphertext
    pan_unique_reference VARCHAR(64)   NOT NULL UNIQUE,
    account_id           UUID          NOT NULL REFERENCES accounts(account_id),
    cvv_hash             VARCHAR(128)  NOT NULL,        -- bcrypt hash
    expiry               VARCHAR(4)    NOT NULL,        -- MMYY
    card_brand           VARCHAR(10)   NOT NULL,
    card_status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    tokenization_allowed BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_card_status  CHECK (card_status IN ('ACTIVE', 'BLOCKED', 'EXPIRED')),
    CONSTRAINT chk_card_brand   CHECK (card_brand  IN ('MC', 'VISA', 'AMEX'))
);

CREATE INDEX idx_cards_pan_ref ON cards (pan_unique_reference);

-- ─── otps ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS otps (
    otp_id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pan_unique_reference VARCHAR(64)  NOT NULL,
    activation_method_id VARCHAR(8)   NOT NULL,
    code_hash            VARCHAR(128) NOT NULL,         -- bcrypt hash of OTP
    delivery_channel     VARCHAR(20)  NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    used                 BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts             SMALLINT     NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_delivery_channel CHECK (delivery_channel IN ('SMS', 'EMAIL')),
    CONSTRAINT chk_attempts         CHECK (attempts >= 0 AND attempts <= 3)
);

CREATE INDEX idx_otps_pan_ref ON otps (pan_unique_reference, activation_method_id);

-- ─── transactions ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    tx_id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pan_unique_reference VARCHAR(64)   NOT NULL,   -- NEVER the real PAN
    amount               NUMERIC(15,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'USD',
    merchant_id          VARCHAR(50),
    merchant_name        VARCHAR(120),
    auth_code            VARCHAR(10),
    status               VARCHAR(20)   NOT NULL,
    decline_reason       VARCHAR(60),
    entry_mode           VARCHAR(30),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tx_status CHECK (status IN ('APPROVED', 'DECLINED'))
);

CREATE INDEX idx_transactions_pan_ref ON transactions (pan_unique_reference);

-- ─── Seed data (simulator test accounts) ──────────────────────────────────────
-- Creates one test account with a £5,000 balance for integration testing.
-- Test card seeding is done at application startup via DataSeeder (not here).
INSERT INTO accounts (account_id, customer_id, account_number, balance, currency, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000099',
    'ACC-TEST-001',
    5000.00,
    'USD',
    'ACTIVE'
) ON CONFLICT DO NOTHING;
