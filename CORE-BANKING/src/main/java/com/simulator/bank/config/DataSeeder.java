package com.simulator.bank.config;

import com.simulator.bank.model.Account;
import com.simulator.bank.model.Card;
import com.simulator.bank.repository.AccountRepository;
import com.simulator.bank.repository.CardRepository;
import com.simulator.bank.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Seeds test card data on application startup (idempotent — skips if cards exist).
 *
 * <h3>Test cards — from README §11.2</h3>
 * <table>
 *   <tr><th>Scenario</th><th>PAN</th><th>Expiry</th><th>CVV</th><th>Balance</th></tr>
 *   <tr><td>MC APPROVED</td><td>5100000000000001</td><td>12/28</td><td>123</td><td>$2,000</td></tr>
 *   <tr><td>MC DECLINED (NSF)</td><td>5100000000000019</td><td>12/28</td><td>456</td><td>$0</td></tr>
 *   <tr><td>VISA APPROVED</td><td>4111111111111111</td><td>06/27</td><td>737</td><td>$5,000</td></tr>
 *   <tr><td>VISA BLOCKED</td><td>4111111111111129</td><td>06/27</td><td>001</td><td>N/A</td></tr>
 * </table>
 *
 * <p>PANs are encrypted via {@link AuthorizationService#encryptPan} before storage.
 * CVVs are bcrypt-hashed — never stored in plaintext.
 * panUniqueReference is derived as SHA-256(PAN) truncated to UUID form, identical to the
 * derivation performed by {@link com.simulator.mdes.service.TokenService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final CardRepository       cardRepository;
    private final AccountRepository    accountRepository;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder      passwordEncoder;


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTestAccountsAndCards();
    }

    private void seedTestAccountsAndCards() {
        // §11.2 Test Card 1 — Mastercard APPROVED
        Account acc1 = ensureAccount("ACC-TEST-001", new BigDecimal("5000.00"), "USD");
        seedCard("5100000000000001", "1228", "123", "MC", acc1, "ACTIVE", true);

        // §11.2 Test Card 2 — Mastercard DECLINED (insufficient funds, $0 balance)
        Account acc2 = ensureAccount("ACC-TEST-002", BigDecimal.ZERO, "USD");
        seedCard("5100000000000019", "1228", "456", "MC", acc2, "ACTIVE", true);

        // §11.2 Test Card 3 — Visa APPROVED
        Account acc3 = ensureAccount("ACC-TEST-003", new BigDecimal("5000.00"), "USD");
        seedCard("4111111111111111", "0627", "737", "VISA", acc3, "ACTIVE", true);

        // §11.2 Test Card 4 — Visa BLOCKED
        Account acc4 = ensureAccount("ACC-TEST-004", new BigDecimal("500.00"), "USD");
        seedCard("4111111111111129", "0627", "001", "VISA", acc4, "BLOCKED", false);

        log.info("DataSeeder — test card seeding complete (4 cards from README §11.2)");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Account ensureAccount(String accountNumber, BigDecimal balance, String currency) {
        return accountRepository.findByAccountNumber(accountNumber).orElseGet(() -> {
            Account account = Account.builder()
                    .customerId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
                    .accountNumber(accountNumber)
                    .balance(balance)
                    .currency(currency)
                    .status("ACTIVE")
                    .build();
            log.info("DataSeeder — created account: {}", accountNumber);
            return accountRepository.save(account);
        });
    }

    private void seedCard(String pan, String expiry, String cvv,
                          String brand, Account account, String cardStatus,
                          boolean tokenizationAllowed) {

        // Derive the panUniqueReference using SHA-256 (same algorithm as MDES TokenService).
        String panUniqueReference = derivePanUniqueReference(pan);

        // Skip if card already seeded (idempotent re-runs).
        if (cardRepository.findByPanUniqueReference(panUniqueReference).isPresent()) {
            log.debug("DataSeeder — card already exists, skipping: brand={}, expiry={}", brand, expiry);
            return;
        }

        // Encrypt PAN using the same AES-256-GCM service used at runtime.
        String encryptedPan = authorizationService.encryptPan(pan);

        // Hash CVV with bcrypt — never stored in plaintext.
        String cvvHash = passwordEncoder.encode(cvv);

        Card card = Card.builder()
                .pan(encryptedPan)
                .panUniqueReference(panUniqueReference)
                .account(account)
                .cvvHash(cvvHash)
                .expiry(expiry)
                .cardBrand(brand)
                .cardStatus(cardStatus)
                .tokenizationAllowed(tokenizationAllowed)
                .build();

        cardRepository.save(card);
        log.info("DataSeeder — seeded {} {} card, panRef={}, status={}",
                brand, cardStatus, panUniqueReference, cardStatus);
    }

    /**
     * Derives panUniqueReference from the raw PAN using SHA-256 truncated to UUID form.
     * This MUST match the identical logic in
     * {@code com.simulator.mdes.service.TokenService#derivePanUniqueReference}.
     */
    private static String derivePanUniqueReference(String pan) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pan.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return String.format("%08x-%04x-%04x-%04x-%012x",
                    java.nio.ByteBuffer.wrap(hash, 0, 4).getInt() & 0xFFFFFFFFL,
                    java.nio.ByteBuffer.wrap(hash, 4, 2).getShort() & 0xFFFFL,
                    java.nio.ByteBuffer.wrap(hash, 6, 2).getShort() & 0xFFFFL,
                    java.nio.ByteBuffer.wrap(hash, 8, 2).getShort() & 0xFFFFL,
                    java.nio.ByteBuffer.wrap(hash, 8, 8).getLong() & 0xFFFFFFFFFFFFL
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
