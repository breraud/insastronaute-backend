package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlaceResetOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlaceResetOutboxRepository extends JpaRepository<PlaceResetOutbox, Long> {
    Optional<PlaceResetOutbox> findByRevision(String revision);
    List<PlaceResetOutbox> findTop20ByOrderByCreatedAtAsc();
}
