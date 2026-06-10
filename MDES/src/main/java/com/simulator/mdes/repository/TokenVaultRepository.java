package com.simulator.mdes.repository;

import com.simulator.mdes.model.TokenVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@code token_vault} table.
 *
 * <p>Provides standard CRUD plus domain-specific lookup methods used by
 * {@link com.simulator.mdes.service.TokenService} and
 * {@link com.simulator.mdes.service.DetokenizationService}.
 */
@Repository
public interface TokenVaultRepository extends JpaRepository<TokenVault, UUID> {

    /**
     * Locate a token by its surrogate PAN (DPAN).
     * Used during transaction authorisation to validate the incoming payment token.
     *
     * @param tokenValue the 16-digit DPAN presented by the wallet
     * @return the matching vault entry, or empty if not found
     */
    Optional<TokenVault> findByTokenValue(String tokenValue);

    /**
     * Locate all tokens associated with a given {@code panUniqueReference}.
     * Used during lifecycle management to suspend/delete all tokens for a card.
     *
     * @param panUniqueReference the opaque card reference from Core Banking
     */
    java.util.List<TokenVault> findAllByPanUniqueReference(String panUniqueReference);

    /**
     * Check if an active token already exists for a given device and card reference.
     * Prevents duplicate provisioning of the same card to the same device.
     */
    boolean existsByPanUniqueReferenceAndDeviceIdAndStatus(
            String panUniqueReference, String deviceId, String status);
}
