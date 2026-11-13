package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.dto.PlaceCanvasEvent;
import fr.insastronaute.webapi.place.dto.PlacePixelAuthorDto;
import fr.insastronaute.webapi.place.dto.PlacePixelRequest;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownAttempt;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownType;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fresque des départements suit la fresque libre en direct, sur son topic : chaque
 * pixel annoncé doit donc dire de quel département il est.
 */
class PlaceDepartmentPixelEventTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceConfigService configService = mock(PlaceConfigService.class);
    private final PlaceCooldownStore cooldownStore = mock(PlaceCooldownStore.class);
    private final PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final PlacePixelPlacementRepository placements = mock(PlacePixelPlacementRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);

    private PlaceService service;
    private PlaceConfig config;
    private AppUser player;
    private UUID gmId;

    @BeforeEach
    void setUp() {
        service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messaging,
                mock(AutoChallengeTriggerPublisher.class), placements);
        service.setDepartmentRepository(departments);

        config = new PlaceConfig();
        config.setActive(true);
        config.setStartDate(Instant.now().minus(Duration.ofHours(1)));
        config.setEndDate(Instant.now().plus(Duration.ofHours(1)));
        config.setCanvasWidth(10);
        config.setCanvasHeight(10);
        config.setBaseCooldownMs(60_000);
        config.setChallengeCooldownMs(30_000);
        config.setChallengeRatio(100);
        config.setShowPixelAuthors(true);
        when(configService.currentConfig()).thenReturn(config);

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setDisplayName("Ada");
        gmId = UUID.randomUUID();
        player.setDepartment(gmId);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(player));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));

        Department gm = mock(Department.class);
        when(gm.getShortName()).thenReturn("GM");
        when(gm.getColor()).thenReturn("#3B82F6");
        when(departments.findById(gmId)).thenReturn(Optional.of(gm));

        when(cooldownStore.tryAcquire(eq(CooldownType.BASE), eq(player.getId()), anyLong()))
                .thenReturn(new CooldownAttempt(true, 0, Instant.now().plusSeconds(60), "owner"));
        when(canvasStore.placePixel(anyInt(), anyInt(), anyString()))
                .thenReturn(new PlaceCanvasDescriptor("1", 4, 10, 10));
    }

    @Test
    void aPixelKeepsItsColourAndTellsWhichDepartmentPlacedIt() {
        service.placePixel("player", new PlacePixelRequest(1, 2, "#E50000"));

        PlaceCanvasEvent event = publishedEvent();
        assertThat(event.color()).isEqualTo("#E50000");
        assertThat(event.departmentColor()).isEqualTo("#3B82F6");
        verify(canvasStore).placePixel(1, 2, "#E50000");
    }

    @Test
    void aPixelFromAPlayerWithoutDepartmentClaimsNone() {
        player.setDepartment((UUID) null);

        service.placePixel("player", new PlacePixelRequest(1, 2, "#E50000"));

        assertThat(publishedEvent().departmentColor()).isNull();
    }

    /**
     * La fresque se calcule depuis la base : annoncer le pixel aux couleurs d'un
     * département emprunté par voyage dans le temps le ferait changer de couleur au
     * rechargement suivant.
     */
    @Test
    void theAnnouncedDepartmentIsTheRecordedOneNotATimeTravelOverride() {
        player.overrideDepartment(UUID.randomUUID());

        service.placePixel("player", new PlacePixelRequest(1, 2, "#E50000"));

        assertThat(publishedEvent().departmentColor()).isEqualTo("#3B82F6");
    }

    @Test
    void theAuthorOfAPixelComesWithItsDepartmentAndWhenItWasPlaced() {
        Instant placedAt = Instant.parse("2026-09-14T18:30:00Z");
        when(canvasStore.descriptor()).thenReturn(new PlaceCanvasDescriptor("1", 4, 10, 10));
        when(placements.findByGenerationAndXAndYOrderByPlacedAtDesc("1", 1, 2, PageRequest.of(0, 1)))
                .thenReturn(List.of(new PlacePixelPlacement("1", 1, 2, "#e50000", player.getId(), placedAt)));

        PlacePixelAuthorDto author = service.pixelAuthor(1, 2).orElseThrow();

        assertThat(author.displayName()).isEqualTo("Ada");
        assertThat(author.department()).isEqualTo("GM");
        assertThat(author.placedAt()).isEqualTo(placedAt);
        assertThat(author.userId()).isNull();
    }

    private PlaceCanvasEvent publishedEvent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/canvas-updates"), payload.capture());
        return (PlaceCanvasEvent) payload.getValue();
    }
}
