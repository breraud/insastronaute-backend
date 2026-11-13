package fr.insastronaute.webapi.site.repository;

import fr.insastronaute.webapi.site.domain.SiteAvailability;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteAvailabilityRepository extends JpaRepository<SiteAvailability, Short> {

    // Redeclarer findById en y ajoutant @EntityGraph est le mecanisme documente par Spring Data
    // JPA pour surcharger le comportement d'une methode CRUD heritee : la valeur derivee du nom
    // de methode ne s'applique pas ici, c'est bien l'implementation de JpaRepository qui est
    // utilisee, avec ce hint de fetch en plus. updatedBy est @ManyToOne(FetchType.LAZY) ; sans ce
    // graphe, toute lecture hors session (spring.jpa.open-in-view=false) plante des que la ligne a
    // un updated_by_user_id non nul.
    @Override
    @EntityGraph(attributePaths = "updatedBy")
    Optional<SiteAvailability> findById(Short id);
}
