package com.simulator.bank.repository;

import com.simulator.bank.model.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<Otp, UUID> {

    /** Find the most recent active OTP for a given card reference and activation method. */
    Optional<Otp> findTopByPanUniqueReferenceAndActivationMethodIdAndUsedFalseOrderByCreatedAtDesc(
            String panUniqueReference, String activationMethodId);
}
