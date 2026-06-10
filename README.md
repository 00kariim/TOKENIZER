💳 Payment Tokenization Simulator

EMVCo-Compliant Architecture · MDES Simulator · Core Banking · Flutter SDK

Simulation complète d’un écosystème de tokenisation de paiement (type Google Pay / Apple Pay) avec TSP, Core Banking et SDK mobile.
📌 Executive Summary

Le Payment Tokenization Simulator reproduit un écosystème réel de paiement tokenisé basé sur les standards EMVCo.

Il est composé de 3 systèmes principaux :

| Composant                            | Rôle                                            |
| ------------------------------------ | ----------------------------------------------- |
| 📱 Flutter SDK                       | Simule Google Pay (Token Requestor)             |
| 🧠 TSP Simulator (Spring Boot :8081) | Simule Mastercard MDES (Token Service Provider) |
| 🏦 Core Banking (Spring Boot :8082)  | Simule la banque émettrice (Issuer)             |

➡️ Communication sécurisée via Nginx API Gateway + JWT interne

🏗️ High-Level Architecture
🔐 Zones du système
Client Zone → Flutter SDK
Gateway Zone → Nginx (TLS 1.3, routing)
Services Zone → TSP + Core Banking
Data Zone → PostgreSQL isolés


🧭 Architecture globale

Flutter SDK
    ↓ HTTPS
Nginx Gateway (TLS 1.3)
    ↓                 ↓
TSP Simulator     Core Banking
(:8081)             (:8082)

    ↓                 ↓
mdes_vault_db     saham_core_db
(PostgreSQL)      (PostgreSQL)


⚙️ Tech Stack

| Layer        | Tech                     |
| ------------ | ------------------------ |
| Mobile       | Flutter 3.x (Dart)       |
| TSP          | Spring Boot 3 + Java 21  |
| Core Banking | Spring Boot 3 + Java 21  |
| DB           | PostgreSQL 16            |
| Gateway      | Nginx                    |
| Security     | JWT + AES-256 + RSA-2048 |
| Infra        | Docker Compose           |

🧠 Database Design
📦 mdes_vault_db (TSP)
token_vault → stockage DPAN
cryptogram_keys → clés cryptographiques
token_lifecycle_log → audit

⚠️ Aucun PAN réel ici


🏦 saham_core_db (Bank)
accounts
cards (PAN réel encrypté)
otps
transactions

⚠️ PAN = AES-256 encrypted at rest

🔌 API Contracts
📍 TSP Simulator (:8081)
POST /mdes/provisioning/tokenize
POST /mdes/provisioning/activate
POST /mdes/transaction/authorize
PUT /mdes/token/{id}/lifecycle


📍 Core Banking (:8082) (internal only)
authorizeService
authorizeTransaction
deliverActivationCode
notifyServiceActivated


🔄 Core Flows
🟢 A. Token Provisioning

Flutter → TSP → Core Banking → OTP → Activation → DPAN Generated → Stored in Vault

💳 B. Payment Flow

NFC Tap
→ Token + Cryptogram
→ TSP Validation
→ Detokenization
→ Core Banking Authorization
→ APPROVED / DECLINED

🔐 C. Token Lifecycle

Suspend Token → Update Vault → Block Cryptogram → Reject Transactions

🧩 Cryptogram Engine
🔐 Core principle
AES-128 token key
ATC (counter)
Merchant + Amount binding
HMAC-SHA256

cryptogram = HMAC_SHA256(key, token + amount + merchant + ATC)

✔ Replay protection
✔ Transaction binding
✔ Device-level security


📱 Flutter SDK Modules

📦 Structure

provisioning/
storage/
transaction/

🔐 Features
PAN encryption (RSA)
Secure storage (flutter_secure_storage)
NFC simulation
Token lifecycle handling


🐳 Infrastructure (Docker)
Stack
Nginx (443 TLS)
TSP (8081)
Core Banking (8082)
PostgreSQL x2

docker-compose up --build



🔐 Security Design

🛡️ Key protections
PAN never leaves Core Banking unencrypted
Internal JWT (service-to-service)
TLS 1.3 only
Cryptogram replay prevention
OTP limited (3 attempts / 10 min TTL)


📊 Threat Model
| Threat          | Mitigation           |
| --------------- | -------------------- |
| PAN leak        | AES-256 + isolation  |
| Replay attack   | ATC + HMAC           |
| API abuse       | JWT + Nginx blocking |
| OTP brute force | TTL + attempt limit  |


🧪 Testing Strategy

Levels
Unit tests (services)
Integration tests (DB + APIs)
Contract tests (TSP ↔ Bank)
E2E tests (Flutter → Payment flow)
Security tests (replay, OTP, access control)


🧾 Test Cards

| Scenario | PAN              |
| -------- | ---------------- |
| Approved | 5100000000000001 |
| Declined | 5100000000000019 |
| Visa OK  | 4111111111111111 |
| Blocked  | 4111111111111129 |


📁 Project Structure

tsp-simulator/
core-banking/
flutter-sdk/
nginx/
db/
docker-compose.yml


📚 Glossary

PAN → Real card number
DPAN → Tokenized card
TSP → Token provider (MDES-like)
ATC → Transaction counter
Cryptogram → Transaction proof
ID&V → Cardholder verification (OTP)


🚀 Summary

Ce projet simule un écosystème complet de paiement tokenisé :

✔ EMVCo-style architecture
✔ Séparation stricte des responsabilités
✔ Sécurité bancaire réaliste
✔ Simulation Google Pay / MDES
✔ Cryptographie transactionnelle

📌 License

Internal / Educational / Simulation use only.