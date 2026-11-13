package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardCollectionSettingsRepository extends JpaRepository<CardCollectionSettings, Short> {
}
