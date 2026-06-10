package com.simulator.bank.repository;

import com.simulator.bank.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {

    /** Look up a card by its opaque cross-DB reference (shared with the TSP). */
    Optional<Card> findByPanUniqueReference(String panUniqueReference);
}
