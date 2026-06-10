💳 Payment Tokenization Simulator




⚠️ EMVCo-style Payment Tokenization System Simulator
Google Pay / Apple Pay / MDES-like architecture for learning, research, and demos.



🎬 Demo




🧠 Overview

A full end-to-end tokenized payment ecosystem simulation:

📱 Flutter SDK → Token Requestor (Google Pay-like)
🧠 TSP Simulator → MDES (Mastercard Digital Enablement Service)
🏦 Core Banking → Issuer Bank System
🌐 Nginx → Secure API Gateway



🏗️ Architecture (High-Level)


flowchart LR
    A[📱 Flutter SDK] -->|HTTPS / TLS 1.3| B[Nginx Gateway]

    B --> C[TSP Simulator :8081]
    B --> D[Core Banking :8082]

    C --> E[(mdes_vault_db)]
    D --> F[(saham_core_db)]

    C <-->|Internal JWT| D



    🔄 Token Provisioning Flow


    sequenceDiagram
    participant App as 📱 Flutter SDK
    participant TSP as 🧠 TSP Simulator
    participant Bank as 🏦 Core Banking

    App->>TSP: POST /tokenize (encrypted PAN)
    TSP->>Bank: authorizeService()
    Bank-->>TSP: OTP required
    TSP-->>App: Activation Methods

    App->>TSP: submit OTP
    TSP->>Bank: notifyServiceActivated()
    TSP-->>App: DPAN + Token



    💳 Payment Flow (Tap to Pay)



    sequenceDiagram
    participant App as 📱 Wallet
    participant POS as 🛒 Merchant
    participant TSP as 🧠 TSP
    participant Bank as 🏦 Issuer

    App->>POS: NFC Tap (Token + Cryptogram)
    POS->>TSP: /transaction/authorize
    TSP->>TSP: Validate Cryptogram + ATC
    TSP->>Bank: authorizeTransaction()
    Bank-->>TSP: APPROVED
    TSP-->>POS: Authorization Response
    POS-->>App: ✅ Payment Success



🔐 Security Model


🛡️ Core Principles
❌ PAN never leaves Core Banking unencrypted
🔐 AES-256-GCM at rest
🔑 RSA encryption (Flutter → TSP)
🔁 HMAC-SHA256 cryptograms
🚫 Replay protection via ATC
⛔ Internal APIs blocked externally



🧩 System Components




📱 Flutter SDK


Card provisioning UI
RSA encryption engine
Secure storage (flutter_secure_storage)
NFC simulation


🧠 TSP Simulator


Token issuance (DPAN)
Cryptogram engine
Token vault
Lifecycle management



🏦 Core Banking


Account ledger
OTP / ID&V system
Real PAN management
Transaction authorization





🗄️ Database Design



erDiagram
    accounts ||--o{ cards : owns
    cards ||--o{ transactions : generates
    cards ||--o{ otps : verifies

    token_vault ||--|| cryptogram_keys : uses
    token_vault ||--o{ token_lifecycle_log : logs



    ⚙️ Tech Stack



    | Layer    | Technology         |
| -------- | ---------------------- |
| Mobile   | Flutter 3              |
| Backend  | Spring Boot 3          |
| DB       | PostgreSQL 16          |
| Gateway  | Nginx                  |
| Security | JWT / AES / RSA / HMAC |
| Infra    | Docker Compose         |




🚀 Quick Start


git clone https://github.com/00kariim/payment-tokenization-simulator
cd payment-tokenization-simulator
docker-compose up --build



📁 Project Structure


tsp-simulator/
core-banking/
flutter-sdk/
nginx/
db/
docker-compose.yml



🔐 Cryptogram Engine



flowchart LR
    A[Token + Amount + Merchant + ATC] --> B[HMAC-SHA256]
    B --> C[First 8 bytes]
    C --> D[Transaction Cryptogram]


✔ Replay protection
✔ Transaction binding
✔ Merchant locking



📊 Token Lifecycle



stateDiagram-v2
    [*] --> ACTIVE
    ACTIVE --> SUSPENDED
    SUSPENDED --> ACTIVE
    ACTIVE --> DELETED



🧪 Testing Strategy



Unit tests (services)
Integration tests (DB + APIs)
Contract tests (TSP ↔ Bank)
E2E tests (Flutter → Payment)
Security tests (replay, OTP brute force)



💳 Test Cards



| Scenario | PAN              |
| -------- | ---------------- |
| APPROVED | 5100000000000001 |
| DECLINED | 5100000000000019 |
| VISA OK  | 4111111111111111 |
| BLOCKED  | 4111111111111129 |



🔥 Highlights



🧠 EMVCo-like architecture
🔐 Real cryptographic flows (HMAC + AES + RSA)
🏦 Banking-grade separation of concerns
📱 Google Pay simulation
🧪 Full end-to-end payment lifecycle




📌 Disclaimer

This project is for educational & simulation purposes only.
Not connected to real payment networks (Visa / Mastercard / Google Pay).