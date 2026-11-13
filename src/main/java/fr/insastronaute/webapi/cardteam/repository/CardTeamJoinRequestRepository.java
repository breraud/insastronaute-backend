package fr.insastronaute.webapi.cardteam.repository;

import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardTeamJoinRequestRepository extends JpaRepository<CardTeamJoinRequest, UUID> {
    @Query("select request from CardTeamJoinRequest request join fetch request.team where request.requester.id = :userId and request.status = fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus.PENDING order by request.createdAt desc")
    List<CardTeamJoinRequest> findPendingForRequester(@Param("userId") UUID userId);

    @Query("select request from CardTeamJoinRequest request join fetch request.requester where request.team.id = :teamId and request.status = fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus.PENDING order by request.createdAt")
    List<CardTeamJoinRequest> findPendingForTeam(@Param("teamId") UUID teamId);

    Optional<CardTeamJoinRequest> findByIdAndStatus(UUID id, CardTeamRequestStatus status);
    List<CardTeamJoinRequest> findAllByStatus(CardTeamRequestStatus status);
    boolean existsByRequesterIdAndStatus(UUID requesterId, CardTeamRequestStatus status);
    void deleteByTeamId(UUID teamId);
}
