package fr.insastronaute.webapi.card.print.repository;

import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardPrintRunRepository extends JpaRepository<CardPrintRun, UUID> {

    boolean existsByTemplateId(UUID templateId);

    @EntityGraph(attributePaths = {"template", "slots", "slots.card", "slots.cardInstance"})
    List<CardPrintRun> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"template", "slots", "slots.card", "slots.cardInstance"})
    List<CardPrintRun> findAllByTemplateIdOrderByCreatedAtDesc(UUID templateId);

    @EntityGraph(attributePaths = {"template", "slots", "slots.card", "slots.cardInstance"})
    @Query("""
            select printRun
            from CardPrintRun printRun
            where printRun.id = :id
            """)
    Optional<CardPrintRun> findWithSlotsById(@Param("id") UUID id);
}
