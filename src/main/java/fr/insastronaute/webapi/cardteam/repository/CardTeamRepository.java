package fr.insastronaute.webapi.cardteam.repository;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CardTeamRepository extends JpaRepository<CardTeam, UUID> {
    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from CardTeam team where team.id = :id")
    Optional<CardTeam> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Recherche admin par nom d'equipe ou par nom de joueur.
     *
     * <p>Le motif arrive deja construit et deja en minuscules, au lieu d'etre
     * concatene dans la requete. Postgres ne sait pas typer un parametre nu
     * pose entre deux {@code ||} : il choisit {@code bytea}, et la requete
     * meurt sur {@code function lower(bytea) does not exist} des la premiere
     * frappe dans le champ. H2, sur lequel tournent les tests, l'accepte sans
     * broncher — la panne ne se voyait donc qu'en production.</p>
     *
     * <p>Le motif tout-venant est {@code %} : il retient toute equipe, ce que
     * faisait auparavant la branche {@code :query is null}.</p>
     */
    @Query("""
            select team from CardTeam team
            where lower(team.name) like :pattern escape '!'
               or exists (
                   select 1 from CardTeamMember member
                   where member.team = team
                     and lower(member.user.displayName) like :pattern escape '!'
               )
            """)
    Page<CardTeam> searchAdminTeams(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Ce joueur a-t-il fonde une equipe qui existe encore ?
     *
     * <p>{@code creator_user_id} est en {@code ON DELETE RESTRICT} : une equipe survit a
     * la sortie de son fondateur, et retient alors sa ligne.</p>
     */
    boolean existsByCreatorId(UUID creatorId);
}
