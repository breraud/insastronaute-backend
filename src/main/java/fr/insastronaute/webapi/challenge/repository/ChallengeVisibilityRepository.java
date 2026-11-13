package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeVisibilitySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeVisibilityRepository extends JpaRepository<ChallengeVisibilitySettings, Boolean> {
}
