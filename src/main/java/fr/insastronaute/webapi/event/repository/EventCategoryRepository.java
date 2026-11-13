package fr.insastronaute.webapi.event.repository;

import fr.insastronaute.webapi.event.domain.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    List<EventCategory> findAllByOrderByNameAsc();

    Optional<EventCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
