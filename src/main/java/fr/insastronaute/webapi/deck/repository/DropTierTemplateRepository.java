package fr.insastronaute.webapi.deck.repository;

import fr.insastronaute.webapi.deck.domain.DropTierTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DropTierTemplateRepository extends JpaRepository<DropTierTemplate, UUID> {

    // Un seul niveau de collection dans le graphe. En joindre deux — les paliers
    // ET leurs cartes — produit un produit cartésien : un palier de onze cartes
    // ressort onze fois, et Hibernate ne déduplique pas une List. Les cartes se
    // chargent ensuite, à la demande, dans la même transaction.
    @EntityGraph(attributePaths = "tiers")
    List<DropTierTemplate> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "tiers")
    Optional<DropTierTemplate> findWithTiersById(UUID id);

    boolean existsByNameIgnoreCase(String name);

    Optional<DropTierTemplate> findByNameIgnoreCase(String name);
}
