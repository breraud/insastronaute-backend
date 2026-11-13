package fr.insastronaute.webapi.card.goofy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoofyCardRedemptionRepository extends JpaRepository<GoofyCardRedemption, UUID> {
}
