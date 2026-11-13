package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Short> {
}
