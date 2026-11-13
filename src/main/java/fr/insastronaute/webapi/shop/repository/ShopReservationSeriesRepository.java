package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopReservationSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShopReservationSeriesRepository extends JpaRepository<ShopReservationSeries, UUID> {
}
