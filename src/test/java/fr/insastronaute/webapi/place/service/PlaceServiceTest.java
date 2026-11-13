package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlacePixelRequest;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownAttempt;
import fr.insastronaute.webapi.place.service.PlaceCooldownStore.CooldownType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import fr.insastronaute.webapi.place.dto.PlaceCanvasEvent;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlaceConfigService configService;
    @Mock private PlaceCooldownStore cooldownStore;
    @Mock private PlaceCanvasStore canvasStore;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    @Mock private PlacePixelPlacementRepository placements;

    @Test
    void userPersistsConsumedBonusPixelRightsSeparatelyFromChallengePoints() {
        assertThat(Arrays.stream(AppUser.class.getDeclaredFields())
                .map(Field::getName))
                .contains("placeBonusPixelsUsed");
    }

    @Test
    void placementServiceIsAvailable() {
        assertThatCode(() -> Class.forName("fr.insastronaute.webapi.place.service.PlaceService"))
                .doesNotThrowAnyException();
    }

    @Test
    void placementIsRecordedWithItsAuthor() {
        // Sans cette trace, une image choquante ne peut qu'être recouverte : rien
        // ne dit qui l'a dessinée, et la personne recommence trois cases plus loin.
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(activeConfig());
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        when(cooldownStore.tryAcquire(CooldownType.BASE, userId, 28_800_000))
                .thenReturn(new CooldownAttempt(true, 0, Instant.parse("2026-07-23T12:10:00Z"), "owner"));
        when(canvasStore.placePixel(3, 7, "#AABBCC"))
                .thenReturn(new PlaceCanvasDescriptor("active", 5, 10, 10));

        service.placePixel("player", new PlacePixelRequest(3, 7, "#AABBCC"));

        ArgumentCaptor<PlacePixelPlacement> saved = ArgumentCaptor.forClass(PlacePixelPlacement.class);
        verify(placements).save(saved.capture());
        assertThat(saved.getValue().getX()).isEqualTo(3);
        assertThat(saved.getValue().getY()).isEqualTo(7);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getGeneration()).isEqualTo("active");
        // La couleur est rangée en minuscules : deux écritures du même bleu ne
        // doivent pas passer pour deux couleurs à la relecture.
        assertThat(saved.getValue().getColor()).isEqualTo("#aabbcc");
    }

    @Test
    void bannedPlayerCannotPaintAnything() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setPlaceBanned(true);
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(activeConfig());
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));

        Throwable failure = catchThrowable(
                () -> service.placePixel("player", new PlacePixelRequest(1, 1, "#AABBCC")));

        assertThat(failure).isInstanceOf(ForbiddenException.class);
        // Ni cooldown consommé, ni pixel posé : la sanction s'arrête avant le geste.
        verify(canvasStore, never()).placePixel(anyInt(), anyInt(), any());
        verify(placements, never()).save(any());
    }

    @Test
    void earnedBonusPixelTakesPriorityAndDoesNotSpendChallengePoints() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 50);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        Instant acquiredDeadline = Instant.parse("2026-07-23T12:05:00Z");
        when(cooldownStore.tryAcquire(CooldownType.CHALLENGE, userId, 300_000))
                .thenReturn(new CooldownAttempt(true, 0, acquiredDeadline, "cooldown-owner"));
        when(canvasStore.placePixel(4, 8, "#AABBCC"))
                .thenReturn(new PlaceCanvasDescriptor("active", 22, 10, 10));

        var update = service.placePixel("player", new PlacePixelRequest(4, 8, "#AABBCC"));

        assertThat(user.getChallengePoints()).isEqualTo(50);
        assertThat(user.getPlaceBonusPixelsUsed()).isEqualTo(1);
        assertThat(update.revision()).isEqualTo("22");
        assertThat(update.cooldownType()).isEqualTo(CooldownType.CHALLENGE);
        assertThat(update.remainingCooldownMs()).isEqualTo(300_000);
        assertThat(update.nextPlacementAt()).isEqualTo(acquiredDeadline);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/canvas-updates"), event.capture());
        assertThat(event.getValue()).isEqualTo(PlaceCanvasEvent.pixel(4, 8, "#AABBCC", 22));
    }

    @Test
    void noBonusPixelUsesBaseCooldown() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 49);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        when(cooldownStore.tryAcquire(CooldownType.BASE, userId, 28_800_000))
                .thenReturn(new CooldownAttempt(true, 0));
        when(canvasStore.placePixel(0, 0, "#000000"))
                .thenReturn(new PlaceCanvasDescriptor("active", 23, 10, 10));

        var update = service.placePixel("player", new PlacePixelRequest(0, 0, "#000000"));

        assertThat(user.getPlaceBonusPixelsUsed()).isZero();
        assertThat(update.cooldownType()).isEqualTo(CooldownType.BASE);
        assertThat(update.remainingCooldownMs()).isEqualTo(28_800_000);
    }

    @Test
    void resizeLockRejectionReleasesOnlyAcquiredCooldownAndDoesNotConsumeBonus() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 50);
        when(configService.currentConfig()).thenReturn(activeConfig());
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        when(cooldownStore.tryAcquire(CooldownType.CHALLENGE, userId, 300_000))
                .thenReturn(new CooldownAttempt(true, 0, Instant.now().plusMillis(300_000), "owner"));
        when(canvasStore.placePixel(1, 1, "#AABBCC")).thenThrow(new PlaceResizeLockedException());

        Throwable failure = catchThrowable(() -> new PlaceService(userRepository, configService, cooldownStore,
                canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements).placePixel("player", new PlacePixelRequest(1, 1, "#AABBCC")));

        assertThat(failure).isInstanceOf(PlaceResizeLockedException.class);
        verify(cooldownStore).release(CooldownType.CHALLENGE, userId, "owner");
        assertThat(user.getPlaceBonusPixelsUsed()).isZero();
    }

    @Test
    void inactiveAndInvalidCoordinatesExposeDistinctStableCodes() {
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        PlaceConfig inactive = activeConfig();
        inactive.setActive(false);
        when(configService.currentConfig()).thenReturn(inactive);

        Throwable inactiveFailure = catchThrowable(() ->
                service.placePixel("player", new PlacePixelRequest(0, 0, "#000000")));
        assertThat(((fr.insastronaute.webapi.common.web.CodedBadRequestException) inactiveFailure).getCode())
                .isEqualTo("PLACE_INACTIVE");

        when(configService.currentConfig()).thenReturn(activeConfig());
        Throwable coordinateFailure = catchThrowable(() ->
                service.placePixel("player", new PlacePixelRequest(10, 0, "#000000")));
        assertThat(((fr.insastronaute.webapi.common.web.CodedBadRequestException) coordinateFailure).getCode())
                .isEqualTo("PLACE_INVALID_COORDINATES");
    }

    @Test
    void aPaidPixelSkipsTheCooldownAndChargesTheAnnouncedPrice() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setEventPoints(100L);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        when(cooldownStore.remainingMs(CooldownType.BASE, userId)).thenReturn(12_000L);
        when(canvasStore.placePixel(1, 2, "#112233"))
                .thenReturn(new PlaceCanvasDescriptor("active", 7, 10, 10));

        var update = service.placePixel("player", new PlacePixelRequest(1, 2, "#112233", true));

        assertThat(update.purchased()).isTrue();
        assertThat(update.eventPoints()).isEqualTo(70L);
        // Le prix annoncé pour le suivant a déjà monté d'un cran.
        assertThat(update.nextBonusPrice()).isEqualTo(31L);
        assertThat(user.getPlacePurchasedBonusPixels()).isEqualTo(1L);
        // Le cooldown en cours est lu, jamais pris : c'est ce qu'on achète.
        assertThat(update.remainingCooldownMs()).isEqualTo(12_000L);
        verify(cooldownStore, org.mockito.Mockito.never()).tryAcquire(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void twoPaidPixelsInARowAreRefusedUntilANormalOneIsPlaced() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setEventPoints(1_000L);
        user.setPlaceLastPixelPurchased(true);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));

        Throwable refusal = catchThrowable(() -> service.placePixel("player", new PlacePixelRequest(1, 2, "#112233", true)));

        assertThat(refusal).isInstanceOf(fr.insastronaute.webapi.common.web.CodedBadRequestException.class);
        assertThat(((fr.insastronaute.webapi.common.web.CodedBadRequestException) refusal).getCode())
                .isEqualTo("PLACE_BONUS_CHAINED");
        // Rien n'est débité ni écrit sur le canevas.
        assertThat(user.getEventPoints()).isEqualTo(1_000L);
        verify(canvasStore, org.mockito.Mockito.never()).placePixel(anyInt(), anyInt(), any());
    }

    @Test
    void aNormalPixelReopensTheRightToBuyOne() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setPlaceLastPixelPurchased(true);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));
        when(cooldownStore.tryAcquire(CooldownType.BASE, userId, 28_800_000))
                .thenReturn(new CooldownAttempt(true, 0, Instant.parse("2026-07-23T20:00:00Z"), "owner"));
        when(canvasStore.placePixel(3, 3, "#FFFFFF"))
                .thenReturn(new PlaceCanvasDescriptor("active", 8, 10, 10));

        service.placePixel("player", new PlacePixelRequest(3, 3, "#FFFFFF"));

        assertThat(user.isPlaceLastPixelPurchased()).isFalse();
    }

    @Test
    void aPurchaseIsRefusedWhenTheBalanceIsShort() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setEventPoints(29L);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findWithLockByKeycloakSub("player")).thenReturn(Optional.of(user));

        Throwable refusal = catchThrowable(() -> service.placePixel("player", new PlacePixelRequest(1, 2, "#112233", true)));

        assertThat(refusal).isInstanceOf(fr.insastronaute.webapi.common.web.CodedBadRequestException.class);
        assertThat(((fr.insastronaute.webapi.common.web.CodedBadRequestException) refusal).getCode())
                .isEqualTo("PLACE_BONUS_INSUFFICIENT_EVENT_POINTS");
        assertThat(user.getEventPoints()).isEqualTo(29L);
    }

    @Test
    void thePriceClimbsWithEveryPurchaseAlreadyMade() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setEventPoints(100L);
        user.setPlacePurchasedBonusPixels(5L);
        PlaceConfig config = activeConfig();
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(config);
        when(userRepository.findByKeycloakSub("player")).thenReturn(Optional.of(user));

        var status = service.bonusStatus("player");

        assertThat(status.price()).isEqualTo(35L);
        assertThat(status.affordable()).isTrue();
        assertThat(status.blockedByPreviousPurchase()).isFalse();
        assertThat(status.available()).isTrue();
    }

    @Test
    void theStatusSaysWhyABuyIsClosed() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, 0);
        user.setEventPoints(10L);
        user.setPlaceLastPixelPurchased(true);
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(activeConfig());
        when(userRepository.findByKeycloakSub("player")).thenReturn(Optional.of(user));

        var status = service.bonusStatus("player");

        assertThat(status.affordable()).isFalse();
        assertThat(status.blockedByPreviousPurchase()).isTrue();
        assertThat(status.available()).isFalse();
    }

    /**
     * La visibilite et le refus de poser doivent dire la meme chose.
     *
     * <p>C'est tout l'interet de les faire passer par isOpenAt : sans regle partagee, la
     * tuile pourrait s'afficher sur un evenement arme mais pas encore commence, menant a
     * un canevas sur lequel chaque clic est refuse.</p>
     */
    @Test
    void anEventArmedButOutsideItsWindowIsNotShown() {
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);

        PlaceConfig notStarted = activeConfig();
        notStarted.setStartDate(Instant.now().plusSeconds(3_600));
        notStarted.setEndDate(Instant.now().plusSeconds(7_200));
        when(configService.currentConfig()).thenReturn(notStarted);
        assertThat(service.isEventOpen()).isFalse();

        PlaceConfig over = activeConfig();
        over.setStartDate(Instant.now().minusSeconds(7_200));
        over.setEndDate(Instant.now().minusSeconds(3_600));
        when(configService.currentConfig()).thenReturn(over);
        assertThat(service.isEventOpen()).isFalse();
    }

    /** Desarme, l'evenement reste cache meme si sa fenetre est en cours. */
    @Test
    void anInactiveEventIsNotShownEvenInsideItsWindow() {
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        PlaceConfig disarmed = activeConfig();
        disarmed.setActive(false);
        when(configService.currentConfig()).thenReturn(disarmed);

        assertThat(service.isEventOpen()).isFalse();
    }

    @Test
    void anOpenEventIsShown() {
        PlaceService service = new PlaceService(userRepository, configService, cooldownStore, canvasStore, messagingTemplate, autoChallengeTriggerPublisher, placements);
        when(configService.currentConfig()).thenReturn(activeConfig());

        assertThat(service.isEventOpen()).isTrue();
    }

    private static AppUser user(UUID id, long challengePoints) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setChallengePoints(challengePoints);
        user.setPlaceBonusPixelsUsed(0L);
        return user;
    }

    private static PlaceConfig activeConfig() {
        PlaceConfig config = new PlaceConfig();
        config.setActive(true);
        config.setStartDate(Instant.now().minusSeconds(60));
        config.setEndDate(Instant.now().plusSeconds(60));
        config.setCanvasWidth(10);
        config.setCanvasHeight(10);
        config.setBaseCooldownMs(28_800_000);
        config.setChallengeCooldownMs(300_000);
        config.setChallengeRatio(50);
        return config;
    }
}
