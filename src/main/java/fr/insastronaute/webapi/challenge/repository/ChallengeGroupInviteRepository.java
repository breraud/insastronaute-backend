package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeGroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChallengeGroupInviteRepository extends JpaRepository<ChallengeGroupInvite, UUID> {

    Optional<ChallengeGroupInvite> findByToken(UUID token);

    /** Le code vivant de cette personne sur ce defi : le regenerer prolonge celui-ci. */
    Optional<ChallengeGroupInvite> findByChallengeIdAndSubmitterId(UUID challengeId, UUID submitterId);
}
