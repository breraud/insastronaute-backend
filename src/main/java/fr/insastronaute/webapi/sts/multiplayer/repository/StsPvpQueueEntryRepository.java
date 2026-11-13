package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsPvpQueueEntryRepository extends JpaRepository<StsPvpQueueEntry, UUID> {

    @Query(value = "SELECT id FROM sts_pvp_matchmaking_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer lockMatchmaking();

    Optional<StsPvpQueueEntry> findByUser_KeycloakSub(String keycloakSub);

    List<StsPvpQueueEntry> findByFriendlyOrderByQueuedAtAsc(Boolean friendly);

    /**
     * La file d'un mode donne.
     *
     * <p>Le mode fait partie de la cle et non d'un filtre applique apres coup : un joueur qui
     * cherche un raid ne doit jamais etre tire dans un duel, et l'index pose par la migration
     * V122 couvre exactement ce tri.
     */
    List<StsPvpQueueEntry> findByModeAndFriendlyOrderByQueuedAtAsc(StsPvpMode mode, Boolean friendly);

    /**
     * Combien de joueurs cherchent en ce moment dans cette file.
     *
     * <p>La fraicheur fait partie de la question et non d'un menage prealable : une entree
     * qui ne bat plus est toujours en base jusqu'au prochain appariement, et la compter
     * annoncerait du monde qui n'est plus la. Compter en lecture seule evite aussi de
     * prendre le verrou d'appariement pour un simple affichage.
     */
    long countByModeAndFriendlyAndUpdatedAtAfter(StsPvpMode mode, Boolean friendly, Instant since);

    long deleteByUser_KeycloakSub(String keycloakSub);

    @Modifying
    long deleteByUpdatedAtBefore(Instant cutoff);
}
