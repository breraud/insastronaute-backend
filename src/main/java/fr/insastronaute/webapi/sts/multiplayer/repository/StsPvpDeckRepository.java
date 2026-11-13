package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsPvpDeckRepository extends JpaRepository<StsPvpDeck, UUID> {

    List<StsPvpDeck> findByOwner_KeycloakSubOrderByUpdatedAtDesc(String keycloakSub);

    Optional<StsPvpDeck> findByIdAndOwner_KeycloakSub(UUID id, String keycloakSub);

    /** Tous les decks d'un joueur, le plus recemment modifie d'abord. */
    List<StsPvpDeck> findByOwner_IdOrderByUpdatedAtDesc(UUID ownerId);

    List<StsPvpDeck> findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(String keycloakSub, String selectedCharacter);
}
