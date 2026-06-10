package com.simulator.bank.service;

import com.simulator.bank.model.Account;
import com.simulator.bank.repository.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ledger service — manages account balances.
 *
 * <p>All balance mutations are wrapped in transactions. Every debit is persisted
 * immediately; the calling service ({@link AuthorizationService}) is responsible
 * for writing the corresponding transaction record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LedgerService {

    private final AccountRepository accountRepository;

    /**
     * Returns the current balance for an account.
     *
     * @param accountId the internal account UUID
     * @return the current balance
     */
    public BigDecimal getBalance(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId))
                .getBalance();
    }

    /**
     * Debits the given amount from the account balance.
     * The account must have sufficient funds — callers are responsible for
     * checking balance before calling this method.
     *
     * @param accountId the internal account UUID
     * @param amount    the amount to debit (must be positive)
     * @throws IllegalArgumentException if amount is not positive
     * @throws IllegalStateException    if the resulting balance would be negative
     */
    @Transactional
    public void debit(UUID accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive: " + amount);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient funds — balance would go negative");
        }

        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Ledger debit — accountId={}, amount={}, newBalance={}",
                accountId, amount, newBalance);
    }

    /**
     * Credits the given amount to the account balance (used for test data seeding / refunds).
     *
     * @param accountId the internal account UUID
     * @param amount    the amount to credit (must be positive)
     */
    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive: " + amount);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Ledger credit — accountId={}, amount={}, newBalance={}",
                accountId, amount, account.getBalance());
    }
}
