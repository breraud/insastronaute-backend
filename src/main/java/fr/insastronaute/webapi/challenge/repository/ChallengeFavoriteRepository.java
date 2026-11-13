package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChallengeFavoriteRepository extends JpaRepository<ChallengeFavorite, UUID> {

    List<ChallengeFavorite> findByUserId(UUID userId);

    boolean existsByUserIdAndChallengeId(UUID userId, UUID challengeId);

    @Modifying
    void deleteByUserIdAndChallengeId(UUID userId, UUID challengeId);
}
