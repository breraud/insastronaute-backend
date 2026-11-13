package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.dto.PlaceCensorRequest;
import fr.insastronaute.webapi.place.dto.PlaceCensorResultDto;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceModerationServiceTest {

    @Mock private PlacePixelPlacementRepository placements;
    @Mock private PlaceCanvasStore canvasStore;
    @Mock private PlaceConfigService configService;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AdminActionJournal journal;

    @InjectMocks private PlaceModerationService service;

    private PlaceConfig config() {
        PlaceConfig config = new PlaceConfig();
        config.setCanvasWidth(10);
        config.setCanvasHeight(10);
        return config;
    }

    private AppUser painter(UUID id, String name) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setDisplayName(name);
        return user;
    }

    @Test
    void censoringRepaintsEveryCellOfTheArea() {
        when(configService.currentConfig()).thenReturn(config());
        when(canvasStore.descriptor()).thenReturn(new PlaceCanvasDescriptor("active", 3, 10, 10));
        when(placements.findLatestInArea("active", 2, 3, 4, 5)).thenReturn(List.of());

        PlaceCensorResultDto result = service.censor(
                new PlaceCensorRequest(2, 4, 2, 2, "#ffffff"), "moderator-sub");

        assertThat(result.pixelsCleared()).isEqualTo(4);
        verify(canvasStore).placePixel(2, 4, "#ffffff");
        verify(canvasStore).placePixel(3, 5, "#ffffff");
        // Chaque case effacée est diffusée : les autres joueurs regardent la même
        // toile, et une censure qui n'arrive qu'au rechargement laisse l'image
        // visible sur tous les écrans déjà ouverts.
        verify(messagingTemplate, times(4)).convertAndSend(eq("/topic/canvas-updates"), any(Object.class));
    }

    @Test
    void censoringNamesWhoHadPaintedTheArea() {
        // Recouvrir sans imputer ne sert à rien : la personne recommence ailleurs.
        UUID author = UUID.randomUUID();
        when(configService.currentConfig()).thenReturn(config());
        when(canvasStore.descriptor()).thenReturn(new PlaceCanvasDescriptor("active", 3, 10, 10));
        when(placements.findLatestInArea("active", 1, 1, 1, 1)).thenReturn(List.of(
                new PlacePixelPlacement("active", 1, 1, "#000000", author, Instant.parse("2026-09-01T10:00:00Z"))));
        when(userRepository.findAllById(List.of(author))).thenReturn(List.of(painter(author, "Ada")));

        PlaceCensorResultDto result = service.censor(
                new PlaceCensorRequest(1, 1, 1, 1, "#ffffff"), "moderator-sub");

        assertThat(result.authors()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.displayName()).isEqualTo("Ada");
                    assertThat(dto.userId()).isEqualTo(author);
                });
        verify(journal).recordGlobal(eq("moderator-sub"), eq(AdminActionType.PLACE_CANVAS_CENSORED), anyMap());
    }

    @Test
    void banningClosesTheCanvasAndLeavesTheRestOpen() {
        UUID userId = UUID.randomUUID();
        AppUser user = painter(userId, "Ada");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.setBanned(userId, true, "moderator-sub");

        assertThat(user.getPlaceBanned()).isTrue();
        verify(userRepository).save(user);
        verify(journal).record(eq("moderator-sub"), eq(userId), eq(AdminActionType.PLACE_BANNED), anyMap());
    }

    @Test
    void liftingABanIsJournalledAsItsOwnGesture() {
        UUID userId = UUID.randomUUID();
        AppUser user = painter(userId, "Ada");
        user.setPlaceBanned(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.setBanned(userId, false, "moderator-sub");

        assertThat(user.getPlaceBanned()).isFalse();
        verify(journal).record(eq("moderator-sub"), eq(userId), eq(AdminActionType.PLACE_UNBANNED), anyMap());
    }

    /**
     * La colonne couleur doit etre un varchar, comme l'entite le declare.
     *
     * <p>Flyway ne tourne pas dans les tests -- le schema vient d'Hibernate -- donc rien
     * ici ne relit ce SQL. Et sous {@code ddl-auto=validate}, un ecart de type n'est pas
     * un avertissement : la validation echoue et le backend refuse de demarrer. Cette
     * assertion est le seul garde-fou entre les deux.</p>
     */
    @org.junit.jupiter.api.Test
    void thePixelColourColumnIsAVarcharLikeTheEntityDeclares() throws java.io.IOException {
        java.nio.file.Path migration =
                java.nio.file.Path.of("src/main/resources/db/migration/V133__place_pixel_color_is_varchar.sql");
        org.assertj.core.api.Assertions.assertThat(migration).exists();

        String sql = java.nio.file.Files.readString(migration).toLowerCase(java.util.Locale.ROOT);
        org.assertj.core.api.Assertions.assertThat(sql)
                .contains("alter table place_pixel_placements")
                .contains("alter column color type varchar(7)");
    }
}
