package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CollectionTestSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionTestSettingsRepository extends JpaRepository<CollectionTestSettings, Boolean> {
}
