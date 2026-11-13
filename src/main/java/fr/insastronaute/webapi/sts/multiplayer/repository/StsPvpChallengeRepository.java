package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallenge;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsPvpChallengeRepository extends JpaRepository<StsPvpChallenge, UUID> {

    List<StsPvpChallenge> findByChallenged_KeycloakSubAndStatusOrderByCreatedAtDesc(String keycloakSub, StsPvpChallengeStatus status);

    Optional<StsPvpChallenge> findByIdAndChallenged_KeycloakSub(UUID id, String keycloakSub);

    /** Les défis reçus par un joueur dans un état donné, le plus récent d'abord. */
    List<StsPvpChallenge> findByChallenged_IdAndStatusOrderByCreatedAtDesc(UUID challengedId, StsPvpChallengeStatus status);

    /** Les défis lancés par un joueur dans un état donné, le plus récent d'abord. */
    List<StsPvpChallenge> findByChallenger_IdAndStatusOrderByCreatedAtDesc(UUID challengerId, StsPvpChallengeStatus status);

    /** Le défi en cours de l'un vers l'autre, s'il y en a un — pour ne pas en empiler dix. */
    Optional<StsPvpChallenge> findFirstByChallenger_IdAndChallenged_IdAndStatusOrderByCreatedAtDesc(
            UUID challengerId, UUID challengedId, StsPvpChallengeStatus status);

    /**
     * Le défi, tenu pour la durée de la transaction.
     *
     * <p>Accepter, refuser et annuler écrivent tous l'état du même défi, et deux de ces gestes
     * peuvent arriver ensemble — le défié qui accepte au moment où le défiant annule. Sans ce
     * verrou, les deux lisent PENDING, et l'annulation passe sur une bataille déjà ouverte.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from StsPvpChallenge challenge where challenge.id = :id")
    Optional<StsPvpChallenge> findByIdForUpdate(@Param("id") UUID id);
}
