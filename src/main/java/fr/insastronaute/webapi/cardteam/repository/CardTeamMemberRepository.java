package fr.insastronaute.webapi.cardteam.repository;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardTeamMemberRepository extends JpaRepository<CardTeamMember, UUID> {
    @Query("select member from CardTeamMember member join fetch member.team where member.user.id = :userId")
    Optional<CardTeamMember> findByUserId(@Param("userId") UUID userId);

    /**
     * Toutes les appartenances, avec leur equipe.
     *
     * <p>Le classement de collection groupe par equipe : il lui faut la carte
     * complete d'un coup, pas une requete par joueur classe.</p>
     */
    @Query("select member from CardTeamMember member join fetch member.user join fetch member.team")
    List<CardTeamMember> findAllWithTeam();

    @Query("select member from CardTeamMember member join fetch member.user where member.team.id = :teamId order by member.joinedAt")
    List<CardTeamMember> findByTeamIdOrderByJoinedAt(@Param("teamId") UUID teamId);

    /**
     * Les membres de plusieurs equipes en une requete.
     *
     * <p>La recherche admin rend une page d'equipes ; les charger une par une
     * faisait une requete par equipe, soit vingt-et-une pour une page de vingt.
     * L'equipe est ramenee avec, pour que le regroupement cote service ne
     * declenche pas a son tour une requete par ligne.</p>
     */
    @Query("select member from CardTeamMember member join fetch member.user join fetch member.team "
            + "where member.team.id in :teamIds order by member.joinedAt")
    List<CardTeamMember> findByTeamIdInOrderByJoinedAt(@Param("teamIds") Collection<UUID> teamIds);

    long countByTeamId(UUID teamId);

    @Query("select distinct member.team from CardTeamMember member where member.user.id in :userIds order by member.team.name")
    List<CardTeam> findTeamsContainingUsers(@Param("userIds") Collection<UUID> userIds);

    void deleteByTeamId(UUID teamId);
}
