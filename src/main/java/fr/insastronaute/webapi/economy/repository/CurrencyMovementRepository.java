package fr.insastronaute.webapi.economy.repository;

import fr.insastronaute.webapi.economy.domain.CurrencyMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CurrencyMovementRepository extends JpaRepository<CurrencyMovement, UUID> {

    /** Les mouvements d'une fenetre, du plus ancien au plus recent. */
    @Query("""
            select movement
            from CurrencyMovement movement
            where movement.occurredAt >= :from and movement.occurredAt < :to
            order by movement.occurredAt
            """)
    List<CurrencyMovement> findInWindow(@Param("from") Instant from, @Param("to") Instant to);
}
