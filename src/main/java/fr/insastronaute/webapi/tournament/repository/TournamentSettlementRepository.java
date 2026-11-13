package fr.insastronaute.webapi.tournament.repository;

import fr.insastronaute.webapi.tournament.domain.TournamentSettlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentSettlementRepository extends JpaRepository<TournamentSettlement, UUID> {

    @EntityGraph(attributePaths = "tiers")
    Optional<TournamentSettlement> findByEventId(UUID eventId);

    List<TournamentSettlement> findByEventIdIn(Collection<UUID> eventIds);
}
