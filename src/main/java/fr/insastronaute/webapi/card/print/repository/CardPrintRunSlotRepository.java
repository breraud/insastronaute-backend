package fr.insastronaute.webapi.card.print.repository;

import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardPrintRunSlotRepository extends JpaRepository<CardPrintRunSlot, UUID> {

    @EntityGraph(attributePaths = {"printRun", "card", "cardInstance"})
    List<CardPrintRunSlot> findByCardInstanceIdIn(Collection<UUID> cardInstanceIds);

    @EntityGraph(attributePaths = {"printRun", "card", "cardInstance"})
    Optional<CardPrintRunSlot> findByCardInstanceId(UUID cardInstanceId);

    long countByCardId(UUID cardId);
}
