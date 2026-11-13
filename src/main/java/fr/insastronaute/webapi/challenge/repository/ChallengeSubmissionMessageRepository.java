package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmissionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChallengeSubmissionMessageRepository extends JpaRepository<ChallengeSubmissionMessage, UUID> {

    List<ChallengeSubmissionMessage> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
