package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardInstanceReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CardInstanceRewardRepository extends JpaRepository<CardInstanceReward, UUID> {
    boolean existsByCardInstanceIdAndUserIdAndCardId(UUID cardInstanceId, UUID userId, UUID cardId);
}
