package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsPvpBattleRepository extends JpaRepository<StsPvpBattle, UUID> {

    /**
     * The battle, held for the duration of the transaction.
     *
     * <p>A PvP battle is one combat two clients write to. Reading it under a write lock is what
     * keeps two commands that arrive together from both resolving against the same revision and
     * one of the two outcomes being silently lost.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select battle from StsPvpBattle battle where battle.id = :id")
    Optional<StsPvpBattle> findByIdWithLock(@Param("id") UUID id);

    /**
     * The ids of the battles in a given status.
     *
     * <p>Ids rather than battles on purpose: a sweep that loaded the rows here would work on
     * copies read before it took any lock, and would then have to write them back over whatever a
     * player did meanwhile. Reading only the ids leaves {@link #findByIdWithLock} the one place a
     * battle is ever loaded to be changed.
     */
    @Query("select battle.id from StsPvpBattle battle where battle.status = :status")
    List<UUID> findIdsByStatus(@Param("status") StsPvpBattleStatus status);

    /**
     * Les batailles terminees d'une periode, reduites a leurs equipes et a leur vainqueur.
     *
     * <p>Une projection et non les batailles : une bataille porte aussi tout son etat de combat
     * et son historique de cartes, deux colonnes lourdes dont une statistique d'usage n'a que
     * faire, et qu'il serait absurde de charger bataille par bataille pour les jeter.</p>
     *
     * <p>{@code since} n'est jamais nul — l'appelant passe l'epoque pour « depuis toujours ». Un
     * parametre date nul fait echouer l'inference de type de Postgres sur certains pilotes.</p>
     *
     * @return des paires {@code [teams, winnerTeam]}
     */
    @Query("""
            select battle.teams, battle.winnerTeam from StsPvpBattle battle
            where battle.status = fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus.FINISHED
              and battle.createdAt >= :since
              and battle.mode in :modes
              and (:includeFriendly = true or battle.friendly = false)
            """)
    List<Object[]> findFinishedTeamsAndWinners(
            @Param("since") java.time.Instant since,
            @Param("modes") java.util.Collection<fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode> modes,
            @Param("includeFriendly") boolean includeFriendly);
}
