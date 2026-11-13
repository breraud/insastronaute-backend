package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreasureHuntPathRepository extends JpaRepository<TreasureHuntPath, UUID> {
    List<TreasureHuntPath> findAllByOrderByPositionAsc();

    /**
     * Les voies dans un ordre qui ne renseigne pas le joueur.
     *
     * La position d'une voie est la combinaison du coffre. Servie a un joueur -- en
     * clair dans un champ, ou seulement par l'ordre du tableau JSON -- elle rend
     * l'enigme du coffre lisible dans l'onglet reseau. Le nom court de departement
     * est stable, et ne dit rien de l'ordre attendu.
     */
    List<TreasureHuntPath> findAllByOrderByDepartmentShortNameAsc();
    Optional<TreasureHuntPath> findByDepartmentId(UUID departmentId);
    Optional<TreasureHuntPath> findByPosition(Integer position);
}
