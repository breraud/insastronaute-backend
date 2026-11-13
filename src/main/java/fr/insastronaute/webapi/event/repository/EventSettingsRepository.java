package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.EventSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSettingsRepository extends JpaRepository<EventSettings, Boolean> {
}
