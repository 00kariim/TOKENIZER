# TOKENIZER




Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 1

## PAYMENT TOKENIZATION
## SIMULATOR
## Full System Design & Architecture
EMVCo-Compliant · MDES Simulator · Core Banking · Flutter SDK
MDES Simulator
## Spring Boot · Port 8081
## Core Banking
## Spring Boot · Port 8082
Flutter SDK
## Google Pay Simulator

## Version 1.0  ·  2025


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 2
Table of Contents
Table of Contents ........................................................................................................................2
- Executive Summary ................................................................................................................4
- High-Level Architecture ...........................................................................................................5
2.1 Architecture Overview........................................................................................................5
2.2 Component Architecture Diagram ......................................................................................5
2.3 Technology Stack ..............................................................................................................6
- Database Design ....................................................................................................................7
3.1 mdes_vault_db — Token Service Provider Database ........................................................7
3.1.1 token_vault..................................................................................................................7
3.1.2 cryptogram_keys .........................................................................................................7
3.1.3 token_lifecycle_log ......................................................................................................7
3.2 saham_core_db — Core Banking Database ......................................................................8
3.2.1 accounts .....................................................................................................................8
3.2.2 cards ...........................................................................................................................8
3.2.3 otps .............................................................................................................................8
3.2.4 transactions.................................................................................................................9
3.3 ERD Overview ...................................................................................................................9
- API Contracts ........................................................................................................................11
4.1 TSP Simulator Endpoints (Port 8081) ..............................................................................11
4.2 Core Banking Endpoints (Port 8082) ...............................................................................11
4.3 Detailed JSON Contracts .................................................................................................11
4.3.1 POST /api/v1/mdes/provisioning/tokenize .................................................................11
4.3.2 POST /api/v1/mdes/provisioning/activate ..................................................................12
4.3.3 POST /api/v1/mdes/transaction/authorize .................................................................13
4.3.4 TSP → Bank Internal: POST /api/v1/core/authorizeService ......................................13
4.3.5 TSP → Bank: POST /api/v1/core/authorizeTransaction.............................................14
- Sequence Flows ...................................................................................................................15
5.1 Flow A: Token Provisioning (Adding a card to Google Pay) .............................................15
5.2 Flow B: Transaction (Tap to Pay).....................................................................................15
5.3 Flow C: Token Lifecycle — Suspend ...............................................................................16
- Service Internal Design .........................................................................................................17
6.1 TSP Simulator Service — Internal Architecture................................................................17
6.2 Core Banking Service — Internal Architecture .................................................................17
6.3 Cryptogram Engine — Technical Detail ...........................................................................18

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 3
6.4 Flutter SDK — Module Architecture .................................................................................18
6.4.1 Provisioning Module ..................................................................................................18
6.4.2 Secure Storage Module.............................................................................................18
6.4.3 Transaction Module ...................................................................................................19
- Infrastructure & Deployment ..................................................................................................20
7.1 Docker Compose — Full Stack ........................................................................................20
7.2 Nginx Configuration .........................................................................................................21
7.3 Project Directory Structure ...............................................................................................22
- Security Design .....................................................................................................................24
8.1 Threat Model & Mitigations ..............................................................................................24
8.2 Key Management ............................................................................................................24
8.3 Data Classification ...........................................................................................................25
- Spring Boot Implementation Guide ........................................................................................26
9.1 TSP Simulator — Key Dependencies (pom.xml) ..............................................................26
9.2 Token Generation — Core Logic .....................................................................................26
9.3 Cryptogram Engine ..........................................................................................................28
9.4 Flutter SDK — Provisioning Service ................................................................................29
- Configuration & Environment...............................................................................................31
10.1 Environment Variables (.env.example) ..........................................................................31
10.2 Spring Boot application.yml — TSP Simulator ...............................................................31
10.3 Flutter pubspec.yaml Dependencies ..............................................................................32
- Testing Strategy ..................................................................................................................33
11.1 Test Levels ....................................................................................................................33
11.2 Test Card Data ..............................................................................................................33
- Glossary ..............................................................................................................................34



Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 4
## 1. Executive Summary
This document provides the complete system design and architecture for a Payment
Tokenization Simulator that adheres to EMVCo specifications. The simulator replicates a real-
world tokenization ecosystem using three distinct components:

## Component Role
Flutter Mobile SDK Simulates Google Pay (Token Requestor). Handles card provisioning
UI, secure token storage, cryptogram generation, and NFC/in-app
payment simulation.
TSP Simulator Service Simulates Mastercard MDES (Token Service Provider). Central engine
for token lifecycle: issuance, provisioning, cryptogram validation, and
de-tokenization.
Core Banking Service Simulates the Card Issuer (Bank). Manages accounts, card records,
identity verification (ID&V), OTP delivery, and transaction authorization.

The three services communicate over a controlled internal network via an Nginx API Gateway.
The entire stack runs locally using Docker Compose, making it suitable for EMVCo compliance
demonstrations, developer education, and security research.

## Design Principles
- Strict separation: PAN never leaves the Core Banking domain in plain text
- Real EMVCo API contracts between TSP and Bank (authorizeService, deliverActivationCode,
notifyServiceActivated)
- Dynamic single-use token cryptograms per transaction (ARQC-style)
- Token Domain Restriction Controls enforced at de-tokenization
- All inter-service traffic authenticated via internal JWT


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 5
- High-Level Architecture
## 2.1 Architecture Overview
The system is divided into four zones, each with a distinct security boundary:

## Zone Description
Client Zone Flutter mobile application running on Android/iOS emulator or physical
device. Communicates exclusively with the Gateway Zone.
Gateway Zone Nginx reverse proxy. Handles SSL termination, request routing, and rate
limiting. Only external-facing entry point.
Services Zone Two Spring Boot microservices: TSP Simulator (port 8081) and Core
Banking (port 8082). Service-to-service calls use internal JWT
authentication.
Data Zone Two isolated PostgreSQL databases: mdes_vault_db (token mappings,
cryptogram keys) and saham_core_db (accounts, cards, OTPs). Databases
cannot cross-communicate.

## 2.2 Component Architecture Diagram
## ┌──────────────────────────────────────────────────────────────
## ───┐
## │                        CLIENT ZONE                             │
## │   ┌───────────────────────────────────────────────────────┐   │
│   │           Flutter SDK (Google Pay Simulator)          │   │
## │   │   ┌────────────┐  ┌─────────────┐  ┌─────────────┐  │   │
│   │   │Provisioning│  │  Secure     │  │ Transaction │  │   │
│   │   │    UI      │  │  Storage    │  │   Engine    │  │   │
## │   │   └────────────┘  └─────────────┘  └─────────────┘  │   │
## │   └───────────────────────────┬───────────────────────────┘   │
## └───────────────────────────────│──────────────────────────────
## ───┘
## HTTPS / TLS 1.3
## ┌───────────────────────────────▼─────────────────────────────
## ────┐
## │                       GATEWAY ZONE                              │
## │              ┌──────────────────────────┐                      │
## │              │    Nginx Reverse Proxy   │                      │
## │              │  /api/v1/mdes/* → :8081  │                      │
## │              │  /api/v1/core/* → :8082  │                      │
## │              └───────┬──────────┬────────┘                      │
## └──────────────────────│──────────│────────────────────────────
## ────┘

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 6
│          │  Internal mTLS / JWT
## ┌──────────────────────▼──────────▼───────────────────────────
## ─────┐
## │                      SERVICES ZONE                               │
## │  ┌───────────────────────────┐  ┌───────────────────────────┐   │
│  │   TSP Simulator Service   │  │  Core Banking Service     │   │
## │  │       Port 8081           │◄─►│       Port 8082           │   │
│  │  • Token Provisioning API │  │  • ID&V / OTP Module      │   │
## │  │  • Token Vault Engine     │  │  • Account Ledger         │   │
## │  │  • Cryptogram Engine      │  │  • Authorization Engine   │   │
│  │  • De-tokenization API    │  │  • Notification Service   │   │
## │  └────────────┬──────────────┘  └────────────┬──────────────┘   │
## └───────────────│─────────────────────────────────│────────────
## ─────┘
│                                 │  Isolated DBs
## ┌───────────────▼─────────────┐
## ┌───────────────▼──────────────────┐
│      mdes_vault_db          │ │        saham_core_db              │
│   PostgreSQL : 5432         │ │     PostgreSQL : 5433             │
│  • token_vault              │ │  • accounts                       │
│  • cryptogram_keys          │ │  • cards                          │
│  • token_lifecycle_log      │ │  • otps                           │
│                             │ │  • transactions                   │
## └─────────────────────────────┘
## └────────────────────────────────────┘

## 2.3 Technology Stack
## Layer Technology Purpose
Flutter SDK Dart / Flutter 3.x Google Pay simulator, NFC emulation,
secure storage
TSP Simulator Spring Boot 3.x, Java 21 Token Service Provider, MDES simulator
Core Banking Spring Boot 3.x, Java 21 Card issuer, ledger, ID&V
API Gateway Nginx 1.25+ TLS termination, routing, rate limiting
Databases PostgreSQL 16 Two isolated instances (vault + core)
Container Orchestration Docker Compose Local dev, all services orchestrated
Service Authentication JWT (internal) Service-to-service auth tokens
Encryption AES-128 / RSA-2048 PAN encryption, key management


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 7
## 3. Database Design
3.1 mdes_vault_db — Token Service Provider Database
This database is exclusively owned by the TSP Simulator Service. It must never be accessible
to the Core Banking Service. The PAN is never stored here; only a logical reference
(panUniqueReference) ties the token back to the bank.

3.1.1 token_vault
CREATE TABLE token_vault (
token_id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
token_value          CHAR(16)     NOT NULL UNIQUE,        -- The surrogate PAN
## (DPAN)
pan_unique_reference VARCHAR(64)  NOT NULL,               -- Opaque ref to real
PAN in Core DB
token_expiry         CHAR(4)      NOT NULL,               -- MMYY format
wallet_id            VARCHAR(20)  NOT NULL,               -- e.g. 214 = Google
## Pay
token_requestor_id   VARCHAR(30)  NOT NULL,               -- e.g. GOOGLEPAY_001
device_id            VARCHAR(128),                        -- Bound device
fingerprint
token_type           VARCHAR(20)  NOT NULL DEFAULT 'DEVICE_SPECIFIC',
status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE /
## SUSPENDED / DELETED
assurance_level      SMALLINT     NOT NULL DEFAULT 0,
domain_restriction   JSONB,                               -- Allowed presentment
modes
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.1.2 cryptogram_keys
CREATE TABLE cryptogram_keys (
key_id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
token_id             UUID         NOT NULL REFERENCES token_vault(token_id) ON
## DELETE CASCADE,
symmetric_key        TEXT         NOT NULL,               -- AES-128 key,
encrypted at rest
atc                  INTEGER      NOT NULL DEFAULT 0,     -- Application
## Transaction Counter
key_expiry           TIMESTAMPTZ  NOT NULL,
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.1.3 token_lifecycle_log
CREATE TABLE token_lifecycle_log (
log_id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 8
token_id             UUID         NOT NULL,
event_type           VARCHAR(40)  NOT NULL,   -- ISSUED, SUSPENDED, DELETED,
## TX_APPROVED, TX_DECLINED
actor                VARCHAR(60),             -- wallet_id or merchant_id
payload_summary      TEXT,                    -- NON-SENSITIVE summary only,
never PAN
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.2 saham_core_db — Core Banking Database
This database is exclusively owned by the Core Banking Service. The real PAN lives here. No
other service has direct DB access.

3.2.1 accounts
CREATE TABLE accounts (
account_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
customer_id          UUID         NOT NULL,
account_number       VARCHAR(20)  NOT NULL UNIQUE,
balance              NUMERIC(15,2) NOT NULL DEFAULT 0.00,
currency             CHAR(3)      NOT NULL DEFAULT 'USD',
status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / FROZEN
## / CLOSED
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.2.2 cards
CREATE TABLE cards (
pan                  CHAR(16)     PRIMARY KEY,            -- Real PAN, AES-
encrypted at rest
pan_unique_reference VARCHAR(64)  NOT NULL UNIQUE,        -- Opaque reference
shared with TSP
account_id           UUID         NOT NULL REFERENCES accounts(account_id),
cvv_hash             VARCHAR(128) NOT NULL,               -- bcrypt hash of CVV
expiry               CHAR(4)      NOT NULL,               -- MMYY
card_brand           VARCHAR(10)  NOT NULL,               -- MC / VISA / AMEX
card_status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
tokenization_allowed BOOLEAN      NOT NULL DEFAULT TRUE,
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.2.3 otps
CREATE TABLE otps (
otp_id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
pan_unique_reference VARCHAR(64)  NOT NULL,
activation_method_id CHAR(8)      NOT NULL,              -- e.g. A1B2C3D4

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 9
code_hash            VARCHAR(128) NOT NULL,               -- bcrypt hash of 6-
digit OTP
delivery_channel     VARCHAR(20)  NOT NULL,               -- SMS / EMAIL
expires_at           TIMESTAMPTZ  NOT NULL,               -- Short TTL: 10
minutes
used                 BOOLEAN      NOT NULL DEFAULT FALSE,
attempts             SMALLINT     NOT NULL DEFAULT 0,     -- Max 3 before lockout
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.2.4 transactions
CREATE TABLE transactions (
tx_id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
pan_unique_reference VARCHAR(64)  NOT NULL,
amount               NUMERIC(15,2) NOT NULL,
currency             CHAR(3)      NOT NULL DEFAULT 'USD',
merchant_id          VARCHAR(50),
merchant_name        VARCHAR(120),
auth_code            VARCHAR(10),
status               VARCHAR(20)  NOT NULL,              -- APPROVED / DECLINED
decline_reason       VARCHAR(60),
entry_mode           VARCHAR(30),                        -- NFC / ECOM / MANUAL
created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
## );

3.3 ERD Overview
## Entity Relationship Summary
saham_core_db                             mdes_vault_db

accounts (1) ──────── (N) cards           token_vault (1) ─── (1) cryptogram_keys
account_id               pan               token_id              token_id
customer_id              pan_unique_ref ◄── pan_unique_ref
balance                  account_id FK      token_value
status                   cvv_hash           wallet_id         token_lifecycle_log
expiry             status                 token_id FK
cards (1) ──── (N) otps                         domain_restriction     event_type
pan_unique_ref           otp_id                                    created_at
pan_unique_ref FK
cards (1) ──── (N) transactions               Logical link (not a FK constraint):
pan_unique_ref           pan_unique_ref FK  cards.pan_unique_reference
= token_vault.pan_unique_reference
## Security Note
The pan_unique_reference column acts as a logical bridge between the two databases.
It is an opaque, non-guessable token (UUID-like string). It does NOT contain PAN data.

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 10
No foreign key constraint crosses DB boundaries. This is intentional — strict isolation.
The TSP service can never query saham_core_db directly, and vice versa.


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 11
- API Contracts
4.1 TSP Simulator Endpoints (Port 8081)
These endpoints are called by the Flutter SDK (Token Requestor) and the Merchant simulator.

## Method Endpoint Description
POST /api/v1/mdes/provisioning/tokenize Initiate card tokenization — Flutter sends
encrypted PAN
POST /api/v1/mdes/provisioning/activate Submit OTP to complete provisioning
POST /api/v1/mdes/transaction/authorize Merchant sends Token + Cryptogram for
payment
POST /api/v1/mdes/transaction/detokenize Internal: resolve Token → PAN (called by
above)
GET /api/v1/mdes/token/{tokenValue}/status Query token status
## (ACTIVE/SUSPENDED/DELETED)
PUT /api/v1/mdes/token/{tokenValue}/lifecycle Suspend or delete a token

4.2 Core Banking Endpoints (Port 8082)
These endpoints are called exclusively by the TSP Simulator Service (service-to-service),
authenticated via internal JWT. Flutter never calls these directly.

## Method Endpoint Description
POST /api/v1/core/authorizeService TSP → Bank: verify card + return OTP methods
POST /api/v1/core/deliverActivationCode TSP → Bank: trigger OTP delivery to
cardholder
POST /api/v1/core/notifyServiceActivated TSP → Bank: confirm token activated
POST /api/v1/core/authorizeTransaction TSP → Bank: authorize payment using real
## PAN
GET /api/v1/core/account/{accountId}/balance Internal balance inquiry
POST /api/v1/core/card/lookup Validate pan_unique_reference + return card
metadata

4.3 Detailed JSON Contracts
4.3.1 POST /api/v1/mdes/provisioning/tokenize

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 12
Request — Flutter → TSP Simulator:
## {
"tokenRequestorId": "GOOGLEPAY_001",
"fundingAccountInfo": {
"encryptedPayload": {
"encryptedData": "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkExMjhHQ00ifQ...",
"publicKeyFingerprint": "4c4ead3a...",
"encryptedKey": "MIIBIjANBgkq..."
## }
## },
"deviceInfo": {
"deviceName": "Pixel 9a",
"deviceType": "MOBILE_PHONE",
"deviceId": "a3f8c2d1-...",
"osVersion": "Android 15"
## },
"walletId": "214",
"tokenType": "DEVICE_SPECIFIC"
## }

Response — TSP → Flutter (OTP required):
## {
"requestId": "12345678-1234-1234-1234-123456789012",
"decision": "REQUIRE_ACTIVATION",
"panUniqueReference": "FW4K4000000000000000001",
"activationMethods": [
## {
"activationMethodType": "TEXT_TO_CARDHOLDER_NUMBER",
"activationMethodId": "A1B2C3D4",
"activationMethodValue": "******1234"
## }
## ]
## }

4.3.2 POST /api/v1/mdes/provisioning/activate
Request — Flutter → TSP (OTP submission):
## {
"panUniqueReference": "FW4K4000000000000000001",
"activationMethodId": "A1B2C3D4",
"activationCode": "845123",
"tokenRequestorId": "GOOGLEPAY_001"
## }

Response — TSP → Flutter (success):
## {
"decision": "APPROVED",
"paymentToken": "5412345678901234",
"tokenExpiry": "1230",

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 13
"tokenUniqueReference": "DW5L5000000000000000002",
"tokenAssuranceLevel": 3
## }

4.3.3 POST /api/v1/mdes/transaction/authorize
Request — Merchant → TSP (Token Payment Request):
## {
"paymentToken": "5412345678901234",
"tokenCryptogram": "3F8A9C2B4D6E1F7A",
## "amount": 42.00,
"currency": "USD",
"merchantId": "MCH-PARIS-001",
"merchantName": "CaféBlanc Paris",
"merchantCategoryCode": "5812",
"posEntryMode": "07",
"atc": "001C"
## }

Response — TSP → Merchant:
## {
"transactionId": "tx-98765432",
"status": "APPROVED",
"authorizationCode": "583920",
"par": "V003K000000000000000001",
"responseCode": "00"
## }

4.3.4 TSP → Bank Internal: POST /api/v1/core/authorizeService
Request (called by TSP):
## {
"authorizeServiceRequest": {
"requestId": "12345678-1234-1234-1234-123456789012",
"tokenRequestorId": "GOOGLEPAY_001",
"panUniqueReference": "FW4K4000000000000000001",
"fundingAccountInfo": {
"encryptedPayload": { "encryptedData": "eyJ..." }
## },
"deviceInfo": { "deviceName": "Pixel 9a", "deviceType": "MOBILE_PHONE" },
"walletId": "214",
"tokenType": "CLOUD"
## }
## }

Response (Bank → TSP):
## {
"authorizeServiceResponse": {

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 14
"responseId": "87654321-4321-4321-4321-210987654321",
"decision": "REQUIRE_ACTIVATION",
"activationMethods": [
## {
"activationMethodType": "TEXT_TO_CARDHOLDER_NUMBER",
"activationMethodId": "A1B2C3D4",
"activationMethodValue": "******1234"
## }
## ]
## }
## }

4.3.5 TSP → Bank: POST /api/v1/core/authorizeTransaction
Request (called by TSP during payment, using real PAN):
## {
"authorizeTransactionRequest": {
"requestId": "tx-88888888",
"panUniqueReference": "FW4K4000000000000000001",
## "amount": 42.00,
"currency": "USD",
"merchantId": "MCH-PARIS-001",
"merchantName": "CaféBlanc Paris",
"merchantCategoryCode": "5812",
"posEntryMode": "07"
## }
## }

Response (Bank → TSP):
## {
"authorizeTransactionResponse": {
"responseId": "bank-resp-12345",
"decision": "APPROVED",
"authorizationCode": "583920",
"availableBalance": 1248.50
## }
## }


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 15
## 5. Sequence Flows
5.1 Flow A: Token Provisioning (Adding a card to Google Pay)
## Provisioning Sequence — 9 Steps
Flutter SDK          TSP Simulator              Core Banking
## │                      │                          │
1.│── POST /tokenize ───►│                          │
│  {PAN encrypted,     │                          │
│   deviceInfo,        │                          │
│   walletId}          │                          │
│                    2.│── POST /authorizeService─►│
│                      │  {panUniqueRef, device}  │
│                      │                        3.│ Validate card
│                      │                          │ Generate OTP
## │                      │◄── REQUIRE_ACTIVATION ──│
│                      │   {activationMethodId}   │
## 4.│◄─ REQUIRE_ACTIVATION─│                          │
│  (show OTP prompt)   │                          │
│                    5.│── POST /deliverCode ────►│
│                      │  {activationMethodId}    │
│                      │                        6.│ Send SMS OTP
│                      │◄── {result: SUCCESS} ───│  to cardholder
7.│── POST /activate ───►│                          │
│  {OTP code,          │                          │
│   activationMethodId}│                          │
│                    8.│ Validate OTP             │
│                      │ Generate Token (DPAN)    │
│                      │ Store in vault           │
│                      │── POST /notifyActivated─►│
│                      │  {token, tokenRef}       │
│                      │◄── {result: SUCCESS} ───│
9.│◄─ {token, tokenRef} ─│                          │
│  Store in Secure     │                          │
│  Element / HCE       │                          │

5.2 Flow B: Transaction (Tap to Pay)
## Transaction Sequence — 7 Steps
Flutter SDK         Merchant / POS    TSP Simulator        Core Banking
## │                    │                 │                    │
1.│ Tap NFC terminal   │                 │                    │
## │── Token + ────────►│                 │                    │

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 16
## │   Cryptogram       │                 │                    │
## │  (NFC / APDU)      │                 │                    │
│                  2.│── POST /tx ────►│                    │
## │                    │  {token,        │                    │
│                    │   cryptogram,   │                    │
│                    │   amount}       │                    │
│                    │              3. │ Validate cryptogram│
│                    │                 │ Check ATC          │
│                    │                 │ Check domain restr.│
## │                    │                 │ Detokenize:        │
│                    │                 │  token → PAN_ref   │
│                    │                 │── POST /auth ──────►│
│                    │                 │  {panRef, amount}  │
## │                    │              4. │               Check│
│                    │                 │               funds│
## │                    │                 │◄─ APPROVED ────────│
│                    │              5. │ Log transaction     │
│                    │◄── APPROVED ───│ {authCode, PAR}    │
│                  6.│ Show approval  │                    │
7.│◄─ Beep / confirm ──│                │                    │
│   (PAN never       │                │                    │
│    seen by         │                │                    │
│    merchant)       │                │                    │

## 5.3 Flow C: Token Lifecycle — Suspend
## Lifecycle Sequence
Flutter SDK / Bank App    TSP Simulator              Core Banking
## │                      │                          │
1.│── PUT /lifecycle ───►│                          │
│  {token, action:     │                          │
## │   SUSPEND}           │                          │
│                    2.│ Update token status      │
│                      │ to SUSPENDED in vault    │
│                      │── notifySuspension ─────►│
│                    3.│ Log lifecycle event      │
## │◄── 200 OK ──────────│                          │
## │                      │                          │
Any future transaction attempt with this token:        │
│                    4.│ Cryptogram validation    │
│                      │ fails: token SUSPENDED   │
│◄── DECLINED ────────│ {responseCode: 14}       │


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 17
## 6. Service Internal Design
6.1 TSP Simulator Service — Internal Architecture
The TSP Simulator is a Spring Boot 3 application structured in layers. Each layer has a single
responsibility and depends only on the layer below it.

## Layer Responsibility
Controller Layer REST controllers exposing /api/v1/mdes/* endpoints. Handles HTTP,
request validation (Bean Validation), and serialization. No business
logic.
Service Layer Core business logic: token generation, cryptogram
generation/validation, de-tokenization, domain restriction checks, and
lifecycle management.
Repository Layer JPA repositories for token_vault and cryptogram_keys tables. All
queries return domain objects; raw PAN never appears here.
Client Layer Feign clients to call Core Banking's internal APIs. Carries internal JWT
in Authorization header for every request.
Security Layer JWT filter for inbound requests from Gateway. mTLS configuration for
outbound calls to Core Banking.
Cryptogram Engine Standalone component. Implements ARQC-style HMAC-based
cryptogram generation using the token's symmetric key and ATC
counter.

## 6.2 Core Banking Service — Internal Architecture
## Layer Responsibility
Controller Layer REST controllers for /api/v1/core/* endpoints. Accepts only service-
JWT-authenticated requests from TSP Simulator. Rejects all direct
external calls.
ID&V Module Handles OTP generation (SecureRandom, 6-digit), bcrypt hashing, TTL
enforcement, and attempt counting. Calls NotificationService for
delivery.
Notification Service Stub implementation for simulator: logs OTP to console/test endpoint.
In production this would call an SMS gateway.
Authorization Engine Validates pan_unique_reference → real PAN lookup, checks balance,
applies fraud rules, executes ledger debit, and returns auth decision.
Ledger Manages account balances. Writes to the transactions table on every
authorization attempt.

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 18
PAN Encryption PAN stored AES-256-GCM encrypted at rest. Decrypted only within the
Authorization Engine for balance lookup. Never logged.

## 6.3 Cryptogram Engine — Technical Detail
The cryptogram provides transaction-specific proof that the token is being used by the legitimate
device for this specific transaction. It is computed as follows:

- Retrieve the token's AES-128 symmetric key (K) from cryptogram_keys for the given
token_id.
- Retrieve and increment the ATC (Application Transaction Counter) — prevents
cryptogram replay.
- Construct the input data string:
data = token_value + amount + currency + merchant_id + ATC (hex) + timestamp
- Compute HMAC-SHA256(K, data), take first 8 bytes → that is the Token Cryptogram.
- On validation: recompute from the presented values. Mismatch = reject immediately.

## Replay Protection
The ATC is monotonically increasing and stored server-side. If an attacker re-submits an
old cryptogram with a lower ATC, the validation fails immediately. Each cryptogram is
mathematically bound to the specific transaction amount, merchant, and counter value.

6.4 Flutter SDK — Module Architecture
The Flutter SDK acts as the Token Requestor. It contains three core modules:

## 6.4.1 Provisioning Module
lib/
├── provisioning/
│   ├── ui/
│   │   ├── add_card_screen.dart        // Card capture form
│   │   └── otp_screen.dart             // OTP entry
│   ├── service/
│   │   ├── provisioning_service.dart   // Orchestrates provisioning flow
│   │   └── encryption_service.dart     // RSA-encrypt PAN before sending
│   └── repository/
│       └── token_repository.dart       // Reads/writes from Secure Storage
## 6.4.2 Secure Storage Module
lib/
├── storage/
│   ├── secure_storage_service.dart     // flutter_secure_storage wrapper

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 19
│   └── token_model.dart
│       // Fields: tokenValue, tokenExpiry, tokenUniqueReference, panLastFour
│       // NEVER stores: real PAN, CVV, raw keys
## 6.4.3 Transaction Module
lib/
├── transaction/
│   ├── ui/
│   │   ├── wallet_home_screen.dart     // Shows stored cards
│   │   └── checkout_screen.dart        // Tap to pay UI
│   ├── service/
│   │   ├── payment_service.dart        // Sends token + cryptogram to TSP
│   │   └── nfc_service.dart            // Emulates NFC APDU exchange
│   └── model/
│       └── payment_request.dart        // {token, cryptogram, amount, currency}


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 20
## 7. Infrastructure & Deployment
## 7.1 Docker Compose — Full Stack
The entire stack is orchestrated with a single docker-compose.yml. Services run on an isolated
internal Docker network; only Nginx is exposed externally on port 443.

version: '3.9'

networks:
internal:
driver: bridge
db_network:
driver: bridge
internal: true   # No external access

services:

## # ─── API GATEWAY ─────────────────────────────────────────────
nginx:
image: nginx:1.25-alpine
ports:
## - "443:443"
## - "80:80"
volumes:
## - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
## - ./nginx/certs:/etc/nginx/certs:ro
depends_on: [tsp-simulator, core-banking]
networks: [internal]

## # ─── TSP SIMULATOR ───────────────────────────────────────────
tsp-simulator:
build: ./tsp-simulator
expose: ["8081"]
environment:
SPRING_DATASOURCE_URL: jdbc:postgresql://mdes-db:5432/mdes_vault_db
SPRING_DATASOURCE_USERNAME: mdes_user
## SPRING_DATASOURCE_PASSWORD: ${MDES_DB_PASSWORD}
CORE_BANKING_BASE_URL: http://core-banking:8082
## INTERNAL_JWT_SECRET: ${INTERNAL_JWT_SECRET}
## PAN_ENCRYPTION_KEY: ${PAN_ENCRYPTION_KEY}
depends_on: [mdes-db, core-banking]
networks: [internal, db_network]

## # ─── CORE BANKING ─────────────────────────────────────────────
core-banking:
build: ./core-banking
expose: ["8082"]
environment:
SPRING_DATASOURCE_URL: jdbc:postgresql://core-db:5433/saham_core_db

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 21
SPRING_DATASOURCE_USERNAME: core_user
## SPRING_DATASOURCE_PASSWORD: ${CORE_DB_PASSWORD}
## INTERNAL_JWT_SECRET: ${INTERNAL_JWT_SECRET}
## PAN_ENCRYPTION_KEY: ${PAN_ENCRYPTION_KEY}
depends_on: [core-db]
networks: [internal, db_network]

## # ─── DATABASES ────────────────────────────────────────────────
mdes-db:
image: postgres:16-alpine
expose: ["5432"]
environment:
POSTGRES_DB: mdes_vault_db
POSTGRES_USER: mdes_user
## POSTGRES_PASSWORD: ${MDES_DB_PASSWORD}
volumes:
- mdes_data:/var/lib/postgresql/data
## - ./db/mdes_schema.sql:/docker-entrypoint-initdb.d/01_schema.sql
networks: [db_network]

core-db:
image: postgres:16-alpine
expose: ["5433"]
environment:
POSTGRES_DB: saham_core_db
POSTGRES_USER: core_user
## POSTGRES_PASSWORD: ${CORE_DB_PASSWORD}
volumes:
- core_data:/var/lib/postgresql/data
## - ./db/core_schema.sql:/docker-entrypoint-initdb.d/01_schema.sql
networks: [db_network]

volumes:
mdes_data:
core_data:

## 7.2 Nginx Configuration
upstream tsp_backend {
server tsp-simulator:8081;
## }
upstream core_backend {
server core-banking:8082;
## }

server {
listen 443 ssl;
ssl_certificate     /etc/nginx/certs/server.crt;
ssl_certificate_key /etc/nginx/certs/server.key;
ssl_protocols       TLSv1.3;

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 22

# Flutter SDK calls MDES endpoints
location /api/v1/mdes/ {
proxy_pass http://tsp_backend;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
## }

# Core Banking: BLOCKED from external access
# Only TSP accesses this via internal Docker network directly
location /api/v1/core/ {
deny all;
return 403;
## }
## }

## Security Note
/api/v1/core/* is intentionally blocked at the Nginx layer.
The Core Banking service is only reachable from within the internal Docker network,
meaning only the TSP Simulator service can call it. Flutter and external clients
never have a path to the Core Banking endpoints.

## 7.3 Project Directory Structure
payment-tokenization-simulator/
├── docker-compose.yml
├── .env.example                       # All secrets templated here
├── nginx/
│   ├── nginx.conf
│   └── certs/
│       ├── server.crt
│       └── server.key
├── db/
│   ├── mdes_schema.sql                # Runs on mdes-db startup
│   └── core_schema.sql                # Runs on core-db startup
├── tsp-simulator/                     # Spring Boot service
## │   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/simulator/mdes/
│       ├── MdesApplication.java
│       ├── controller/
│       │   ├── ProvisioningController.java
│       │   └── TransactionController.java
│       ├── service/
│       │   ├── TokenService.java
│       │   ├── CryptogramService.java
│       │   └── DetokenizationService.java
│       ├── repository/

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 23
│       │   ├── TokenVaultRepository.java
│       │   └── CryptogramKeyRepository.java
│       ├── client/
│       │   └── CoreBankingClient.java  // Feign client
│       ├── model/
│       └── config/
│           ├── SecurityConfig.java
│           └── FeignConfig.java
├── core-banking/                      # Spring Boot service
## │   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/simulator/bank/
│       ├── BankApplication.java
│       ├── controller/
│       │   ├── AuthorizeServiceController.java
│       │   └── TransactionController.java
│       ├── service/
│       │   ├── IdvService.java
│       │   ├── OtpService.java
│       │   ├── AuthorizationService.java
│       │   └── LedgerService.java
│       ├── repository/
│       └── config/
│           └── SecurityConfig.java
└── flutter-sdk/                       # Flutter app
├── pubspec.yaml
└── lib/
├── main.dart
├── provisioning/
├── storage/
└── transaction/


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 24
## 8. Security Design
## 8.1 Threat Model & Mitigations
## Threat Level Mitigation
PAN exposure in transit HIGH PAN is RSA-encrypted by Flutter before sending. TSP decrypts
only briefly to compute panUniqueReference. PAN never
logged.
Token replay attack HIGH Token Cryptogram is transaction-bound (ATC + amount +
merchant). Single-use: ATC is incremented server-side on each
use.
## Direct Core Banking
access
HIGH Core Banking blocked at Nginx. Only reachable via internal
Docker network from TSP Simulator, authenticated by internal
## JWT.
Token theft /
enumeration
MEDIUM Tokens pass Luhn check but fail any non-MDES system.
Domain Restriction Controls bind token to specific device/mode.
OTP brute force MEDIUM 3 attempts maximum (locked after). 10-minute TTL. OTP stored
as bcrypt hash only.
Database exposure MEDIUM PAN encrypted AES-256-GCM at rest. Vault DB contains only
panUniqueReference, never real PAN. DBs on isolated internal
Docker network.
Service impersonation LOW All TSP → Bank calls carry internal JWT signed with shared
secret. JWT validated on every request by Core Banking
SecurityConfig.

## 8.2 Key Management
## Key Usage & Notes
PAN Encryption Key AES-256-GCM. Injected via environment variable
(PAN_ENCRYPTION_KEY). Used by Core Banking to encrypt/decrypt
PAN at rest. Must be rotated periodically.
Token Symmetric Keys AES-128 per token, generated by TSP. Stored encrypted in
cryptogram_keys. Used exclusively for HMAC cryptogram computation.
Internal JWT Secret HMAC-SHA256. Shared between TSP and Core Banking via
environment variable. Signs service-to-service authorization tokens
(short TTL: 5 minutes).
TLS Certificates Self-signed for local simulator. Replace with proper CA-signed
certificates for any external deployment.
RSA Public Key TSP publishes its RSA-2048 public key. Flutter uses it to encrypt PAN
payload before transmission. Private key stays server-side only.


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 25
## 8.3 Data Classification
## Data Element Classification Handling
Real PAN (16-digit) TOP SECRET Never leaves Core Banking. Stored AES-
encrypted. Never logged anywhere.
panUniqueReference CONFIDENTIAL Crosses TSP/Bank boundary but is opaque and
non-reversible alone.
CVV TOP SECRET Stored as bcrypt hash only. Never returned in any
API response.
Payment Token (DPAN) INTERNAL Safe to store in device Secure Storage. Useless
without cryptogram.
Token Cryptogram SENSITIVE Single-use. Expires after one transaction or 10
minutes.
Account balance CONFIDENTIAL Returned only to TSP Simulator in authorization
response.
Transaction records INTERNAL Stored with panUniqueReference, not real PAN.


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 26
## 9. Spring Boot Implementation Guide
9.1 TSP Simulator — Key Dependencies (pom.xml)
## <dependencies>
<!-- Web / REST -->
## <dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-web</artifactId>
## </dependency>

<!-- JPA + PostgreSQL -->
## <dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-data-jpa</artifactId>
## </dependency>
## <dependency>
<groupId>org.postgresql</groupId>
<artifactId>postgresql</artifactId>
## </dependency>

<!-- Service-to-service calls (Feign) -->
## <dependency>
<groupId>org.springframework.cloud</groupId>
<artifactId>spring-cloud-starter-openfeign</artifactId>
## </dependency>

<!-- JWT security -->
## <dependency>
<groupId>io.jsonwebtoken</groupId>
<artifactId>jjwt-api</artifactId>
## <version>0.12.3</version>
## </dependency>

## <!-- Validation -->
## <dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-validation</artifactId>
## </dependency>
## </dependencies>

## 9.2 Token Generation — Core Logic
@Service
public class TokenService {

@Autowired private TokenVaultRepository vaultRepo;
@Autowired private CryptogramKeyRepository keyRepo;
@Autowired private CoreBankingClient bankClient;

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 27

public TokenIssuanceResult issueToken(TokenRequest request) {
// 1. Trigger ID&V via Core Banking
AuthorizeServiceResponse idvResult = bankClient.authorizeService(
buildAuthorizeServiceRequest(request)
## );

if (!"REQUIRE_ACTIVATION".equals(idvResult.getDecision())) {
throw new TokenizationException("ID&V failed");
## }

// Store activation method for OTP step (in-memory cache, short TTL)
pendingActivations.put(request.getPanUniqueReference(), idvResult);
return new TokenIssuanceResult(idvResult.getActivationMethods());
## }

@Transactional
public TokenActivationResult activateToken(ActivationRequest req) {
// Validate OTP via Core Banking
bankClient.deliverActivationCode(buildDeliverRequest(req));

// Generate DPAN (surrogate PAN)
String dpan = generateDpan(req.getBrand());   // e.g. 5204 + random 12
digits

// Generate symmetric key for cryptogram engine
byte[] symmetricKey = generateAes128Key();

// Persist to vault
TokenVault entry = new TokenVault();
entry.setTokenValue(dpan);
entry.setPanUniqueReference(req.getPanUniqueReference());
entry.setTokenExpiry("1230");
entry.setStatus("ACTIVE");
entry.setWalletId(req.getWalletId());
entry.setDeviceId(req.getDeviceId());
entry.setDomainRestriction(buildDomainRestriction(req));
TokenVault saved = vaultRepo.save(entry);

// Store cryptogram key
CryptogramKey key = new CryptogramKey();
key.setTokenId(saved.getTokenId());
key.setSymmetricKey(encrypt(symmetricKey));  // Encrypt at rest
key.setAtc(0);
keyRepo.save(key);

// Notify bank
bankClient.notifyServiceActivated(buildNotifyRequest(saved));

return new TokenActivationResult(dpan, "1230", saved.getTokenId());
## }

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 28

private String generateDpan(String brand) {
// Mastercard tokens start with 5204, Visa with 4895 (MDES convention)
String prefix = "MC".equals(brand) ? "5204" : "4895";
SecureRandom sr = new SecureRandom();
StringBuilder sb = new StringBuilder(prefix);
for (int i = 0; i < 12; i++) sb.append(sr.nextInt(10));
return sb.toString();
## }
## }

## 9.3 Cryptogram Engine
@Component
public class CryptogramService {

public String generateCryptogram(String tokenValue, BigDecimal amount,
String currency, String merchantId) {
CryptogramKey keyRecord = keyRepo.findByTokenValue(tokenValue)
.orElseThrow(() -> new InvalidTokenException("Token not found"));

// Increment ATC atomically
int atc = keyRecord.incrementAndGetAtc();
keyRepo.save(keyRecord);

// Build data string
String data = tokenValue + amount.toPlainString() + currency
+ merchantId + String.format("%04X", atc);

// HMAC-SHA256 → first 8 bytes → hex string
byte[] key = decrypt(keyRecord.getSymmetricKey());
byte[] hmac = computeHmacSha256(key,
data.getBytes(StandardCharsets.UTF_8));
return bytesToHex(Arrays.copyOf(hmac, 8));
## }

public boolean validateCryptogram(String tokenValue, String cryptogram,
BigDecimal amount, String currency,
String merchantId, int presentedAtc) {
CryptogramKey keyRecord = keyRepo.findByTokenValue(tokenValue)
.orElseThrow(() -> new InvalidTokenException());

// Replay check: ATC must be greater than stored ATC
if (presentedAtc <= keyRecord.getAtc()) {
log.warn("Cryptogram replay attempt detected for token {}",
tokenValue);
return false;
## }

String data = tokenValue + amount.toPlainString() + currency

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 29
+ merchantId + String.format("%04X", presentedAtc);
byte[] key = decrypt(keyRecord.getSymmetricKey());
byte[] expected = Arrays.copyOf(computeHmacSha256(key, data.getBytes()),
## 8);
return MessageDigest.isEqual(expected, hexToBytes(cryptogram));
## }
## }

9.4 Flutter SDK — Provisioning Service
class ProvisioningService {
final MdesApiClient _mdesClient;
final EncryptionService _encryptionService;
final TokenRepository _tokenRepository;

// Step 1: Request tokenization
Future<TokenizeResponse> requestTokenization({
required String pan,
required String expiry,
required String cvv,
}) async {
// Encrypt PAN with TSP's RSA public key before sending
final encryptedPayload = await _encryptionService.encryptPan(
pan: pan, expiry: expiry, cvv: cvv,
## );

final request = TokenizeRequest(
tokenRequestorId: 'GOOGLEPAY_001',
fundingAccountInfo: FundingAccountInfo(
encryptedPayload: encryptedPayload,
## ),
deviceInfo: await _getDeviceInfo(),
walletId: '214',
tokenType: 'DEVICE_SPECIFIC',
## );

return _mdesClient.tokenize(request);
## }

// Step 2: Submit OTP
Future<TokenActivationResponse> activateToken({
required String panUniqueReference,
required String activationMethodId,
required String otp,
}) async {
final response = await _mdesClient.activate(ActivationRequest(
panUniqueReference: panUniqueReference,
activationMethodId: activationMethodId,
activationCode: otp,
## ));

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 30

if (response.decision == 'APPROVED') {
// Store token in secure storage — NEVER the real PAN
await _tokenRepository.saveToken(TokenModel(
tokenValue: response.paymentToken,
tokenExpiry: response.tokenExpiry,
tokenUniqueReference: response.tokenUniqueReference,
panLastFour: pan.substring(pan.length - 4),  // Only last 4 for display
## ));
## }

return response;
## }
## }


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 31
## 10. Configuration & Environment
## 10.1 Environment Variables (.env.example)
## # ─── DATABASE PASSWORDS ──────────────────────────────────────
MDES_DB_PASSWORD=change_me_mdes_db_password
CORE_DB_PASSWORD=change_me_core_db_password

## # ─── SERVICE AUTHENTICATION ──────────────────────────────────
# Shared between TSP and Core Banking for internal JWT signing
INTERNAL_JWT_SECRET=change_me_minimum_256_bit_random_secret

## # ─── ENCRYPTION KEYS ─────────────────────────────────────────
# AES-256 key for PAN encryption at rest (Base64-encoded)
PAN_ENCRYPTION_KEY=change_me_base64_encoded_aes256_key

## # ─── TSP SIMULATOR ───────────────────────────────────────────
TSP_RSA_PRIVATE_KEY_PATH=/certs/tsp_private.pem
TSP_RSA_PUBLIC_KEY_PATH=/certs/tsp_public.pem

## # ─── FLUTTER SDK ──────────────────────────────────────────────
MDES_BASE_URL=https://localhost:443
TSP_RSA_PUBLIC_KEY=<paste TSP public key here for Flutter build>

10.2 Spring Boot application.yml — TSP Simulator
server:
port: 8081

spring:
datasource:
url: ${SPRING_DATASOURCE_URL}
username: ${SPRING_DATASOURCE_USERNAME}
password: ${SPRING_DATASOURCE_PASSWORD}
jpa:
hibernate:
ddl-auto: validate            # Use Flyway/Liquibase for migrations
show-sql: false                  # NEVER true in production (would log PAN)

mdes:
core-banking-base-url: ${CORE_BANKING_BASE_URL}
internal-jwt-secret: ${INTERNAL_JWT_SECRET}
internal-jwt-ttl-minutes: 5
rsa:
private-key-path: ${TSP_RSA_PRIVATE_KEY_PATH}
public-key-path: ${TSP_RSA_PUBLIC_KEY_PATH}
token:
default-expiry-months: 36
otp-ttl-minutes: 10

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 32
max-otp-attempts: 3

logging:
level:
com.simulator.mdes: INFO
# Ensure no PAN values in log output:
pattern:
console: '%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n'

10.3 Flutter pubspec.yaml Dependencies
dependencies:
flutter:
sdk: flutter

# Secure token storage
flutter_secure_storage: ^9.0.0

# HTTP client
dio: ^5.3.0

# JSON serialization
json_annotation: ^4.8.1

# NFC simulation
flutter_nfc_kit: ^3.3.1

# RSA encryption (for PAN encryption)
pointycastle: ^3.7.3

# Device info (for device binding)
device_info_plus: ^9.1.0

dev_dependencies:
flutter_test:
sdk: flutter
json_serializable: ^6.7.1
build_runner: ^2.4.7
mockito: ^5.4.2


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 33
## 11. Testing Strategy
## 11.1 Test Levels
## Level Scope
Unit Tests Each service layer tested in isolation. CryptogramService: verify HMAC
computation, ATC increment, replay rejection. TokenService: token
generation, Luhn validity. OtpService: hash verification, TTL expiry, attempt
locking.
Integration Tests Spring Boot @SpringBootTest with embedded H2 (or Testcontainers for
PostgreSQL). Test full provisioning flow end-to-end within each service.
Contract Tests Verify TSP → Bank API contracts are honoured exactly. Use Spring Cloud
Contract or WireMock stubs.
End-to-End Tests Flutter integration tests + both services running. Simulate complete
provisioning (add card) then transaction (tap to pay) flow.
Security Tests Attempt cryptogram replay (same ATC) → must fail. Send request to
/api/v1/core/* from external → must 403. Submit wrong OTP 4 times →
must lock. Submit expired OTP → must reject.

## 11.2 Test Card Data
## Scenario Test Data
Mastercard (APPROVED) PAN: 5100000000000001 · Expiry: 12/28 · CVV: 123 · Balance:
## $2,000.00
Mastercard (DECLINED —
## NSF)
PAN: 5100000000000019 · Expiry: 12/28 · CVV: 456 · Balance: $0.00
Visa (APPROVED) PAN: 4111111111111111 · Expiry: 06/27 · CVV: 737 · Balance:
## $5,000.00
Visa (BLOCKED) PAN: 4111111111111129 · Expiry: 06/27 · CVV: 001 · Status:
## BLOCKED


Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 34
## 12. Glossary
## Term Definition
PAN Primary Account Number — the real 16-digit card number issued by
the bank.
DPAN Device PAN — the surrogate/token version of the PAN provisioned to a
specific device.
Token A surrogate PAN (DPAN) that replaces the real PAN during payment.
Useless if intercepted without the cryptogram.
Token Vault Secure mapping table inside MDES that links Token →
panUniqueReference.
Token Cryptogram A single-use, transaction-bound cryptographic code proving this token
is valid for this one specific transaction.
ATC Application Transaction Counter — monotonically increasing counter,
prevents cryptogram replay.
TSP Token Service Provider — the entity that manages token issuance and
the vault (Mastercard MDES, Visa VDES).
MDES Mastercard Digital Enablement Service — Mastercard's implementation
of a Token Service Provider.
Token Requestor Entity that requests tokens on behalf of cardholders (e.g. Google Pay,
## Apple Pay).
## Token Domain Restriction
## Controls
Rules that restrict a token to specific presentment modes (NFC, e-
commerce) and/or a specific device.
ID&V Identification and Verification — the process where the card issuer
verifies the cardholder's identity before issuing a token (typically via
## OTP).
panUniqueReference An opaque, non-sensitive identifier that acts as a logical bridge
between the TSP vault and the Core Banking card record, without
exposing the real PAN.
Token Assurance Level A numeric score (0–3) reflecting how rigorously the cardholder's
identity was verified during tokenization. Higher = more trusted.
HCE Host Card Emulation — Android feature that allows a phone to emulate
an NFC card without a hardware Secure Element.
ARQC Authorization Request Cryptogram — the cryptographic proof in
EMV/contactless payments that Claude simulates with its HMAC-based
cryptogram engine.

Payment Tokenization Simulator — System Design CONFIDENTIAL
© 2025 Payment Tokenization Simulator  ·  EMVCo-Compliant Architecture Page 35
