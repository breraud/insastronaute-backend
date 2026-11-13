package fr.insastronaute.webapi.department.repository;

import fr.insastronaute.webapi.department.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByKeycloakGroupNameIgnoreCase(String keycloakGroupName);

    /**
     * Les filieres, sans les audiences.
     *
     * <p>C'est la liste que veulent tous les ecrans qui decrivent l'ecole : Place, cibles
     * de defis, classement par departement, effectifs theoriques, legendes. Un appelant
     * qui ne demande rien de particulier n'a donc pas PERF, et un ecran ecrit demain non
     * plus -- {@code findAll()} ne reste utile qu'au ciblage des evenements.</p>
     */
    List<Department> findAllByVirtualFalse();

    /** L'audience PERF, le seul departement virtuel a ce jour. */
    Optional<Department> findFirstByVirtualTrue();

}
