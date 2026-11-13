package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardTypeRepository extends JpaRepository<CardType, UUID> {
    boolean existsByNameIgnoreCase(String name);
    Optional<CardType> findByNameIgnoreCase(String name);
}
