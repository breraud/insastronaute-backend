package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository.LatestCellPlacement;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La requête de la fresque des départements, jouée sur une vraie base.
 *
 * <p>Native : les tests du service la simulent, et une erreur d'alias ou de
 * projection ne se verrait qu'en production, sous la forme d'une fresque vide.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class PlacePixelPlacementRepositoryTest {

    @Autowired
    private PlacePixelPlacementRepository placements;

    @Autowired
    private EntityManager entityManager;

    @Test
    void eachCellComesWithItsLatestPlacementAndItsAuthorsDepartment() {
        UUID gm = UUID.randomUUID();
        UUID iti = UUID.randomUUID();
        AppUser ada = user("ada", gm);
        AppUser grace = user("grace", iti);
        AppUser noDepartment = user("sans", null);
        Instant start = Instant.parse("2026-09-15T20:00:00Z");

        placements.save(new PlacePixelPlacement("g1", 1, 2, "#e50000", ada.getId(), start));
        placements.save(new PlacePixelPlacement("g1", 1, 2, "#02be01", grace.getId(), start.plusSeconds(60)));
        placements.save(new PlacePixelPlacement("g1", 3, 0, "#0000ea", ada.getId(), start));
        placements.save(new PlacePixelPlacement("g1", 0, 0, "#222222", noDepartment.getId(), start));
        // Une autre génération du canevas n'a rien à faire dans celle-ci.
        placements.save(new PlacePixelPlacement("g0", 5, 5, "#ffffff", ada.getId(), start));
        placements.flush();
        entityManager.clear();

        List<LatestCellPlacement> cells = placements.findLatestCellsWithDepartment("g1").stream()
                .sorted(Comparator.comparingInt(LatestCellPlacement::getX).thenComparingInt(LatestCellPlacement::getY))
                .toList();

        assertThat(cells).hasSize(3);
        assertThat(cells.get(0).getX()).isZero();
        assertThat(cells.get(0).getDepartmentId()).isNull();
        assertThat(cells.get(1).getX()).isEqualTo(1);
        assertThat(cells.get(1).getY()).isEqualTo(2);
        assertThat(cells.get(1).getColor()).isEqualTo("#02be01");
        assertThat(cells.get(1).getDepartmentId()).isEqualTo(iti);
        assertThat(cells.get(2).getX()).isEqualTo(3);
        assertThat(cells.get(2).getColor()).isEqualTo("#0000ea");
        assertThat(cells.get(2).getDepartmentId()).isEqualTo(gm);
    }

    private AppUser user(String name, UUID departmentId) {
        AppUser user = new AppUser();
        user.setKeycloakSub("sub-" + name + "-" + UUID.randomUUID());
        user.setEmail(name + "@insa-rouen.fr");
        user.setDisplayName(name);
        user.setDepartment(departmentId);
        entityManager.persist(user);
        return user;
    }
}
