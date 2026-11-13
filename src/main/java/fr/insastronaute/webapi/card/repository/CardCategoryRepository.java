package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardCategoryRepository extends JpaRepository<CardCategory, UUID> {

    Optional<CardCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
