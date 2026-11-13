package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StsPvpProfileRepository extends JpaRepository<StsPvpProfile, UUID> {

    Optional<StsPvpProfile> findByUser_KeycloakSub(String keycloakSub);

    /**
     * Le classement de chaque joueur qui a dispute au moins un duel classe, en une requete.
     *
     * <p>Filtre sur les duels joues plutot que sur l'existence du profil : ouvrir le menu
     * multijoueur cree deja un profil a 1000, et ce chiffre-la ne dit rien du joueur.</p>
     *
     * @return des paires {@code [userId, elo]}
     */
    @Query("""
            select profile.user.id, profile.elo from StsPvpProfile profile
            where profile.winsRanked + profile.lossesRanked > 0
            """)
    List<Object[]> findRankedElos();

    /**
     * Les joueurs classes, du meilleur au moins bon, avec de quoi les afficher.
     *
     * <p>Meme population que {@link #findRankedElos()}, pour que la liste de l'administration et
     * le classement public designent les memes personnes.</p>
     */
    @Query("""
            select profile from StsPvpProfile profile join fetch profile.user
            where profile.winsRanked + profile.lossesRanked > 0
            order by profile.elo desc, profile.winsRanked desc, profile.user.id asc
            """)
    List<StsPvpProfile> findRankedByEloDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from StsPvpProfile profile where profile.user.keycloakSub = :keycloakSub")
    Optional<StsPvpProfile> findByUserKeycloakSubForUpdate(@Param("keycloakSub") String keycloakSub);

    // ---- Achievement key phrases -------------------------------------------

    Optional<StsPvpProfile> findByUser_Id(UUID userId);

    @Query("""
            select profile.user.id from StsPvpProfile profile
            where profile.winsRanked + (case when :includeFriendly = true then profile.winsFriendly else 0 end) >= :threshold
              and (:afterUserId is null or profile.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = profile.user.id
                      and awarded.status = 'VALIDATED'
                )
            order by profile.user.id asc
            """)
    List<UUID> findUsersWithPvpWins(
            @Param("threshold") int threshold,
            @Param("includeFriendly") boolean includeFriendly,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select profile.user.id from StsPvpProfile profile
            where profile.elo >= :threshold
              and (:afterUserId is null or profile.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = profile.user.id
                      and awarded.status = 'VALIDATED'
                )
            order by profile.user.id asc
            """)
    List<UUID> findUsersWithElo(
            @Param("threshold") int threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );
}
