package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlaceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceConfigRepository extends JpaRepository<PlaceConfig, Short> {
}
