package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownType;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ce que l'achat d'un pixel bonus annonce du prochain pixel normal.
 *
 * <p>Un joueur qui a des pixels gagnés en défis pose son prochain pixel normal sur
 * le cooldown des défis. L'achat lisait toujours celui de base : il annonçait
 * « disponible », le bouton se rallumait, et la pose était refusée.</p>
 */
class PlaceBonusCooldownTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceConfigService configService = mock(PlaceConfigService.class);
    private final PlaceCooldownStore cooldownStore = mock(PlaceCooldownStore.class);
    private final PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);

    private PlaceService service;
    private PlaceConfig config;
    private AppUser player;

    @BeforeEach
    void setUp() {
        service = new PlaceService(userRepository, configService, cooldownStore, canvasStore,
                mock(SimpMessagingTemplate.class), mock(AutoChallengeTriggerPublisher.class),
                mock(PlacePixelPlacementRepository.class));

        config = new PlaceConfig();
        config.setActive(true);
        config.setStartDate(Instant.now().minus(Duration.ofHours(1)));
        config.setEndDate(Instant.now().plus(Duration.ofHours(1)));
        config.setCanvasWidth(10);
        config.setCanvasHeight(10);
        config.setBaseCooldownMs(300_000);
        config.setChallengeCooldownMs(60_000);
        config.setChallengeRatio(10);
        when(configService.currentConfig()).thenReturn(config);

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setEventPoints(100);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(player));
        when(canvasStore.placePixel(anyInt(), anyInt(), anyString())).thenReturn(new PlaceCanvasDescriptor("1", 7, 10, 10));
    }

    @Test
    void aPurchaseAnnouncesTheChallengeCooldownWhileChallengePixelsRemain() {
        // Cent points de défi à dix points le pixel : il en reste à poser.
        player.setChallengePoints(100L);
        when(cooldownStore.remainingMs(CooldownType.CHALLENGE, player.getId())).thenReturn(45_000L);
        when(cooldownStore.remainingMs(CooldownType.BASE, player.getId())).thenReturn(0L);

        var update = service.placePixel("player", new fr.insastronaute.webapi.place.dto.PlacePixelRequest(1, 2, "#112233", true));

        assertThat(update.cooldownType()).isEqualTo(CooldownType.CHALLENGE);
        assertThat(update.remainingCooldownMs()).isEqualTo(45_000L);
    }

    @Test
    void aPurchaseAnnouncesTheBaseCooldownOnceChallengePixelsAreSpent() {
        player.setChallengePoints(0L);
        when(cooldownStore.remainingMs(CooldownType.BASE, player.getId())).thenReturn(120_000L);
        when(cooldownStore.remainingMs(CooldownType.CHALLENGE, player.getId())).thenReturn(0L);

        var update = service.placePixel("player", new fr.insastronaute.webapi.place.dto.PlacePixelRequest(1, 2, "#112233", true));

        assertThat(update.cooldownType()).isEqualTo(CooldownType.BASE);
        assertThat(update.remainingCooldownMs()).isEqualTo(120_000L);
    }
}
