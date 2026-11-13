package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopReservationSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopReservationSlotRepository extends JpaRepository<ShopReservationSlot, UUID> {
    boolean existsByEventId(UUID eventId);

    @Query("select slot from ShopReservationSlot slot left join fetch slot.event left join fetch slot.series")
    List<ShopReservationSlot> findAllWithEventAndSeries();

    @Query("select slot from ShopReservationSlot slot left join fetch slot.event left join fetch slot.series where slot.id = :id")
    Optional<ShopReservationSlot> findWithEventAndSeriesById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ShopReservationSlot slot left join fetch slot.event left join fetch slot.series where slot.id = :id")
    Optional<ShopReservationSlot> findWithEventAndSeriesForUpdateById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ShopReservationSlot slot where slot.series.id = :seriesId")
    List<ShopReservationSlot> findBySeriesIdForUpdate(@Param("seriesId") UUID seriesId);

    @Modifying
    @Query(value = """
            update shop_reservation_slots
            set active = false, updated_at = CURRENT_TIMESTAMP
            where event_id = :eventId and active = true
            """, nativeQuery = true)
    int deactivateByEventId(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = """
            update shop_reservation_slots
            set event_starts_at_snapshot = :startsAt,
                event_ends_at_snapshot = :endsAt,
                event_location_snapshot = :location,
                updated_at = CURRENT_TIMESTAMP
            where event_id = :eventId
            """, nativeQuery = true)
    int updateEventSnapshots(
            @Param("eventId") UUID eventId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("location") String location
    );
}
