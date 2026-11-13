package fr.insastronaute.webapi.department;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PERF est une audience, et rien ne doit pouvoir en faire le departement de quelqu'un.
 */
@DataJpaTest
@ActiveProfiles("test")
class DepartmentRepositoryTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    void theDepartmentListLeavesOutVirtualDepartments() {
        Department filiere = save("Genie Civil", "GC", "GC", false);
        save("Filieres par apprentissage", "PERF", null, true);

        assertThat(departmentRepository.findAllByVirtualFalse())
                .extracting(Department::getId)
                .containsExactly(filiere.getId());
    }

    /**
     * L'invariant, et la raison pour laquelle il tient tout seul.
     *
     * <p>La connexion et le balayage nocturne cherchent tous deux un departement par son
     * nom de groupe Keycloak. PERF n'en a pas : aucune reponse de l'annuaire ne peut donc
     * le designer, y compris si un groupe de ce nom apparait un jour dans le royaume.</p>
     */
    @Test
    void noKeycloakGroupCanEverDesignateThePerfAudience() {
        save("Filieres par apprentissage", "PERF", null, true);

        assertThat(departmentRepository.findByKeycloakGroupNameIgnoreCase("PERF")).isEmpty();
        assertThat(departmentRepository.findByKeycloakGroupNameIgnoreCase("perf")).isEmpty();
    }

    @Test
    void thePerfAudienceIsTheVirtualDepartment() {
        save("Genie Civil", "GC", "GC", false);
        Department perf = save("Filieres par apprentissage", "PERF", null, true);

        assertThat(departmentRepository.findFirstByVirtualTrue())
                .get()
                .extracting(Department::getId)
                .isEqualTo(perf.getId());
    }

    private Department save(String name, String shortName, String keycloakGroupName, boolean virtual) {
        Department department = new Department();
        department.setName(name);
        department.setShortName(shortName);
        department.setKeycloakGroupName(keycloakGroupName);
        department.setVirtual(virtual);
        return departmentRepository.saveAndFlush(department);
    }
}
