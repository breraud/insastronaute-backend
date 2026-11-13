package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntVault;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreasureHuntVaultRepository extends JpaRepository<TreasureHuntVault, Boolean> { }
