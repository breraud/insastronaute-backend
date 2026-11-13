package fr.insastronaute.webapi.cardteam.repository;

import fr.insastronaute.webapi.cardteam.domain.CardTeamInvitation;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardTeamInvitationRepository extends JpaRepository<CardTeamInvitation, UUID> {
    /**
     * <p>Le {@code left join} sur l'equipe n'est pas cosmetique : une invitation qui
     * propose d'en fonder une n'en a pas encore, et un {@code join} interne la ferait
     * disparaitre de la liste -- l'invitation existerait en base sans jamais s'afficher.</p>
     */
    @Query("select invitation from CardTeamInvitation invitation left join fetch invitation.team join fetch invitation.inviter where invitation.invited.id = :userId and invitation.status = fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus.PENDING order by invitation.createdAt desc")
    List<CardTeamInvitation> findPendingForInvitedUser(@Param("userId") UUID userId);

    @Query("select invitation from CardTeamInvitation invitation left join fetch invitation.team join fetch invitation.invited where invitation.inviter.id = :userId and invitation.status = fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus.PENDING order by invitation.createdAt desc")
    List<CardTeamInvitation> findPendingFromInviter(@Param("userId") UUID userId);

    Optional<CardTeamInvitation> findByIdAndStatus(UUID id, CardTeamRequestStatus status);
    List<CardTeamInvitation> findAllByStatus(CardTeamRequestStatus status);
    boolean existsByInvitedIdAndStatus(UUID invitedId, CardTeamRequestStatus status);
    void deleteByTeamId(UUID teamId);
}
