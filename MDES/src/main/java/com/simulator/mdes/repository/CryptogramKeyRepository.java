package com.simulator.mdes.repository;

import com.simulator.mdes.model.CryptogramKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@code cryptogram_keys} table.
 *
 * <p>The ATC (Application Transaction Counter) must be incremented atomically to
 * prevent replay attacks. All reads that precede ATC increments use
 * {@link LockModeType#PESSIMISTIC_WRITE} to serialise concurrent transactions.
 */
@Repository
public interface CryptogramKeyRepository extends JpaRepository<CryptogramKey, UUID> {

    /**
     * Retrieve the cryptogram key for a given token, applying a pessimistic write
     * lock to serialise ATC increments under concurrent load.
     *
     * @param tokenId the internal token UUID from {@code token_vault}
     * @return the key record, or empty if this token has no key (should not happen)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ck FROM CryptogramKey ck WHERE ck.token.tokenId = :tokenId")
    Optional<CryptogramKey> findByTokenIdForUpdate(UUID tokenId);

    /**
     * Non-locking lookup for read-only operations (key expiry checks, etc.).
     *
     * @param tokenId the internal token UUID
     */
    @Query("SELECT ck FROM CryptogramKey ck WHERE ck.token.tokenId = :tokenId")
    Optional<CryptogramKey> findByTokenId(UUID tokenId);
}
