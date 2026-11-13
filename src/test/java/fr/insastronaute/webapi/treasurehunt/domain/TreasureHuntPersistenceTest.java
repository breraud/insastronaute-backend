package fr.insastronaute.webapi.treasurehunt.domain;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntContentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TreasureHuntPersistenceTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TreasureHuntPathRepository pathRepository;

    @Autowired
    private PlayerCheckpointProgressRepository progressRepository;

    @Autowired
    private TreasureHuntContentRepository contentRepository;

    @Test
    void storesAPathCheckpointAndPlayerProgress() {
        Department department = new Department();
        department.setName("Informatique");
        department.setShortName("INFO");
        department = departmentRepository.saveAndFlush(department);

        TreasureHuntPath path = new TreasureHuntPath();
        path.setDepartment(department);
        path.setPosition(1);
        path.setName("Voie Informatique");

        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setStepNumber(1);
        checkpoint.setCodeHash(HexFormat.of().parseHex("0".repeat(64)));
        TreasureHuntContent content = new TreasureHuntContent();
        content.setPosition(1);
        content.setType(TreasureHuntContentType.RICH_TEXT);
        content.setText("Bienvenue");
        checkpoint.addContent(content);
        path.addCheckpoint(checkpoint);
        path = pathRepository.saveAndFlush(path);

        AppUser player = new AppUser();
        player.setKeycloakSub("player-sub");
        player.setEmail("player@example.com");
        player = userRepository.saveAndFlush(player);

        PlayerCheckpointProgress progress = new PlayerCheckpointProgress();
        progress.setUser(player);
        progress.setCheckpoint(path.getCheckpoints().getFirst());
        progressRepository.saveAndFlush(progress);

        assertThat(pathRepository.findByDepartmentId(department.getId())).contains(path);
        assertThat(progressRepository.existsByUserIdAndCheckpointId(player.getId(), checkpoint.getId())).isTrue();
        assertThat(contentRepository.findByCheckpointIdOrderByPositionAsc(checkpoint.getId()))
                .extracting(TreasureHuntContent::getText)
                .containsExactly("Bienvenue");
    }
}
