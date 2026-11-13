package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreasureHuntSettingsRepository extends JpaRepository<TreasureHuntSettings, Boolean> { }
