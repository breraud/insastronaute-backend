package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    /**
     * Lesquels de ces defis existent encore.
     *
     * <p>Une page d'historique nomme des defis par identifiant, et certains ont ete
     * supprimes depuis. Une seule requete pour toute la page : un existsById par
     * ligne en ferait vingt-cinq.</p>
     */
    @Query("""
            select challenge.id from Challenge challenge
             where challenge.id in :ids and challenge.deletedAt is null
            """)
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);


    /**
     * Récupère les défis destinés à un joueur : ceux qui sont globaux, et ceux qui
     * ciblent son département.
     *
     * Volontairement sans condition de date. Un défi terminé ou pas encore ouvert
     * reste visible ; c'est la soumission qui est bornée, par
     * ChallengeService.validateChallengeActive. Filtrer ici reviendrait d'ailleurs à
     * comparer au CURRENT_TIMESTAMP de la base, quand la soumission compare au
     * TimeTravelContext : sous voyage dans le temps, les deux se contredisaient.
     */
    @EntityGraph(attributePaths = "prerequisiteChallenges")
    @Query("""
        SELECT c FROM Challenge c\s
        WHERE c.deletedAt IS NULL\s
        AND (c.targetDepartments IS EMPTY OR :departmentId IN (SELECT td.id FROM c.targetDepartments td))
   \s""")
    List<Challenge> findChallengesForDepartment(@Param("departmentId") UUID departmentId);

    /**
     * Même chose, mais restreint aux défis dans leur fenêtre.
     *
     * Sert au pourcentage d'avancement affiché sur un profil : le dénominateur y est
     * le nombre de défis réellement faisables au moment du calcul. Y faire entrer les
     * défis à venir ferait chuter le pourcentage de tout le monde.
     */
    @Query("""
        SELECT c FROM Challenge c\s
        WHERE c.deletedAt IS NULL\s
        AND (c.startDate IS NULL OR c.startDate <= CURRENT_TIMESTAMP)\s
        AND (c.endDate IS NULL OR c.endDate >= CURRENT_TIMESTAMP)\s
        AND (c.targetDepartments IS EMPTY OR :departmentId IN (SELECT td.id FROM c.targetDepartments td))
   \s""")
    List<Challenge> findActiveChallengesForDepartment(@Param("departmentId") UUID departmentId);

    /**
     * Les défis automatiques ouverts, et ceux clos depuis peu.
     *
     * <p>Les deux dates sont exigées. Le panneau appelle « brouillon » un défi à
     * qui il en manque une, et un brouillon n'est pas disponible : le laisser
     * passer ici attribuait le défi dès sa création, avant qu'il soit programmé.</p>
     */
    @Query("""
        SELECT c FROM Challenge c
        WHERE c.deletedAt IS NULL
        AND c.validationMode = 'AUTO'
        AND c.startDate IS NOT NULL AND c.startDate <= :now
        AND c.endDate IS NOT NULL AND c.endDate >= :cutoff
        """)
    List<Challenge> findActiveAutomaticChallenges(@Param("now") Instant now, @Param("cutoff") Instant cutoff);

    /**
     * Les défis du jour qu'un bilan de période doit à {@code candidate}.
     *
     * <p>DAILY en principale ou en secondaire, non supprimés, entièrement dans la
     * période, et qui le visent. Les bilans eux-mêmes n'en sont pas : classé DAILY,
     * un bilan s'exigerait lui-même, et deux bilans du même jour l'un l'autre.</p>
     *
     * <p>Partagé en fragment parce que les trois requêtes du bilan doivent dire la
     * même chose ; une divergence donnerait une progression complète que le
     * balayage refuse, ou l'inverse.</p>
     */
    String DAILY_DUE_TO_CANDIDATE = """
            daily.deletedAt is null
            and (daily.category = 'DAILY' or 'DAILY' member of daily.extraCategories)
            and (daily.autoChallengeType is null or daily.autoChallengeType <> :summaryType)
            and daily.startDate >= :startInclusive
            and daily.endDate < :endExclusive
            and (daily.targetDepartments is empty
                 or candidate.departmentId in (select target.id from daily.targetDepartments target))
            """;

    /** {@code candidate} a fait valider {@code daily}, seul ou en groupe. */
    String DAILY_DONE_BY_CANDIDATE = """
            exists (
                select 1 from ChallengeSubmission done
                left join done.participants participant
                where done.challenge.id = daily.id
                  and done.status = 'VALIDATED'
                  and (done.user.id = candidate.id or participant.id = candidate.id)
            )
            """;

    @Query("select count(daily) from Challenge daily, AppUser candidate where candidate.id = :userId and "
            + DAILY_DUE_TO_CANDIDATE)
    long countDailyChallengesDue(
            @Param("userId") UUID userId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("summaryType") String summaryType
    );

    @Query("select count(daily) from Challenge daily, AppUser candidate where candidate.id = :userId and "
            + DAILY_DUE_TO_CANDIDATE + " and " + DAILY_DONE_BY_CANDIDATE)
    long countDailyChallengesCompleted(
            @Param("userId") UUID userId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("summaryType") String summaryType
    );

    /**
     * Les joueurs à qui la période devait au moins un défi du jour, et qui les ont
     * tous faits, sauf ceux qui ont déjà le bilan.
     */
    @Query("""
            select candidate.id
            from AppUser candidate
            where (:afterUserId is null or candidate.id > :afterUserId)
              and not exists (
                  select 1 from ChallengeSubmission awarded
                  where awarded.challenge.id = :challengeId
                    and awarded.user.id = candidate.id
                    and awarded.status = 'VALIDATED'
              )
              and exists (select 1 from Challenge daily where\s"""
            + DAILY_DUE_TO_CANDIDATE + """
              )
              and not exists (select 1 from Challenge daily where\s"""
            + DAILY_DUE_TO_CANDIDATE + " and not " + DAILY_DONE_BY_CANDIDATE + """
              )
            order by candidate.id asc
            """)
    List<UUID> findUsersCompletingAllDailyChallenges(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("summaryType") String summaryType,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Le renommage d'une categorie suit aussi les defis supprimes.
     *
     * <p>Sans quoi un defi restaure reviendrait avec le nom d'avant, qui ne designe
     * plus rien : la restauration doit rendre un defi utilisable, pas un defi casse.
     * C'est la raison pour laquelle ce depot filtre methode par methode au lieu de
     * porter une restriction globale sur l'entite.</p>
     */
    @Modifying
    @Query("update Challenge c set c.category = :newCategory where c.category = :oldCategory")
    int updateCategory(@Param("oldCategory") String oldCategory, @Param("newCategory") String newCategory);

    // ---- Les lectures generiques, reecrites pour ignorer la corbeille -----------
    //
    // Redeclarees ici plutot que filtrees chez les dix appelants : un seul endroit ou
    // la regle s'ecrit, et aucun risque d'en oublier un. Qui veut un defi supprime le
    // demande explicitement, par findDeletedById ou findAllDeleted.

    @Override
    @Query("select c from Challenge c where c.id = :id and c.deletedAt is null")
    Optional<Challenge> findById(@Param("id") UUID id);

    @Override
    @Query("select c from Challenge c where c.deletedAt is null")
    List<Challenge> findAll();

    @Override
    @Query("select c from Challenge c where c.id in :ids and c.deletedAt is null")
    List<Challenge> findAllById(@Param("ids") Iterable<UUID> ids);

    @Override
    @Query("select count(c) from Challenge c where c.deletedAt is null")
    long count();

    @Override
    @Query("select case when count(c) > 0 then true else false end from Challenge c where c.id = :id and c.deletedAt is null")
    boolean existsById(@Param("id") UUID id);

    // ---- La corbeille ----------------------------------------------------------

    /** Un defi supprime, nomme explicitement : la restauration passe par ici. */
    @Query("select c from Challenge c where c.id = :id and c.deletedAt is not null")
    Optional<Challenge> findDeletedById(@Param("id") UUID id);

    /** La corbeille, du plus recemment supprime au plus ancien. */
    @Query("select c from Challenge c where c.deletedAt is not null order by c.deletedAt desc")
    List<Challenge> findAllDeleted();

}
