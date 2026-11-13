package fr.insastronaute.webapi.deck.repository;

import fr.insastronaute.webapi.deck.domain.Deck;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeckRepository extends JpaRepository<Deck, UUID> {

    @EntityGraph(attributePaths = "dropTiers")
    Optional<Deck> findWithDropTiersById(UUID id);
}
