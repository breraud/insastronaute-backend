package fr.insastronaute.webapi.card.goofy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoofyCardSettingsRepository extends JpaRepository<GoofyCardSettings, Short> {

    /**
     * La carte vient avec.
     *
     * <p>Le contrôleur d'administration lit son nom pour l'afficher, une fois la
     * lecture du dépôt terminée, donc hors session : un proxy paresseux y éclate.
     * Tant qu'aucune carte n'était configurée, la référence était nulle et rien ne
     * se dépliait — la panne n'apparaît qu'au premier réglage, sur
     * {@code GET /api/admin/cards/goofy}.</p>
     */
    @Query("""
            select settings
            from GoofyCardSettings settings
            left join fetch settings.card
            where settings.id = :id
            """)
    Optional<GoofyCardSettings> findWithCardById(@Param("id") Short id);
}
