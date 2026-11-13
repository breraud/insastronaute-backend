package fr.insastronaute.webapi.event.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.event.dto.EventRegistrantDto;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import fr.insastronaute.webapi.event.domain.EventSource;
import fr.insastronaute.webapi.event.dto.EventDto;
import fr.insastronaute.webapi.event.dto.EventFriendDto;
import fr.insastronaute.webapi.event.dto.EventRequest;
import fr.insastronaute.webapi.event.dto.EventRegistrationDto;
import fr.insastronaute.webapi.event.repository.EventCategoryRepository;
import fr.insastronaute.webapi.event.repository.EventQuestionRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.event.service.EventQuestionnaireService;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.shop.repository.ShopReservationSlotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.event.service.EventSettingsService;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import fr.insastronaute.webapi.notification.service.NotificationService;
import org.springframework.transaction.PlatformTransactionManager;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventCategoryRepository categoryRepository;

    @Mock
    private EventRegistrationRepository registrationRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private ShopReservationSlotRepository reservationSlotRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    @Mock
    private EventQuestionnaireService questionnaireService;

    @Mock
    private EventQuestionRepository questionRepository;

    /**
     * L'inscription tourne en deux transactions - la place, puis la reponse - pour
     * que la construction du DTO ne se fasse pas sous le verrou de l'evenement. Un
     * gestionnaire simule suffit ici : TransactionTemplate execute la lambda telle
     * quelle, et propage l'exception comme le ferait une vraie transaction.
     */
    @Mock
    private NotificationService notificationService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private EventService eventService;

    @AfterEach
    void clearTimeTravel() {
        TimeTravelContext.clear();
    }

    @Mock
    private EventSettingsService eventSettings;

    @Mock
    private RealmRoleDirectory realmRoleDirectory;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                categoryRepository,
                registrationRepository,
                departmentRepository,
                articleRepository,
                userRepository,
                friendshipRepository,
                reservationSlotRepository,
                s3Client,
                s3Properties(), autoChallengeTriggerPublisher, questionnaireService, questionRepository,
                notificationService, eventSettings, realmRoleDirectory, transactionManager);
    }

    @Test
    void deleteEventDeactivatesReservationSlotsAndFlushesDatabaseBeforeDeletingCover() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        event.setCoverImageObjectKey("events/" + eventId + "/cover/current.png");
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));

        eventService.deleteEvent(eventId);

        InOrder order = inOrder(reservationSlotRepository, eventRepository, s3Client);
        order.verify(reservationSlotRepository).deactivateByEventId(eventId);
        order.verify(reservationSlotRepository).flush();
        order.verify(eventRepository).delete(event);
        order.verify(eventRepository).flush();
        order.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void registerAddsUserToWaitlistWhenQuotaIsFullAndWaitlistIsEnabled() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        Event event = event(eventId);
        event.setCapacity(1);
        event.setWaitlistEnabled(true);

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        // La reponse se construit dans une seconde transaction, hors du verrou : elle
        // recharge l'evenement avec son graphe au lieu de le tenir depuis la premiere.
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.countByEventIdAndStatus(eventId, EventRegistrationStatus.REGISTERED)).thenReturn(1L);
        // Plus de stub pour le decompte de la liste d'attente : le DTO ne le demande
        // plus evenement par evenement, il vient de statusCountsByEventIds, groupee.
        when(registrationRepository.save(any(EventRegistration.class))).thenAnswer(invocation -> {
            EventRegistration registration = invocation.getArgument(0);
            registration.setId(UUID.randomUUID());
            return registration;
        });

        var response = eventService.register(eventId, "sub");

        assertThat(response.status()).isEqualTo(EventRegistrationStatus.WAITLISTED);
    }

    @Test
    void retryingRegistrationReturnsTheExistingSeatWithoutWritingAgain() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        Event event = event(eventId);
        EventRegistration existing = registration(event, user, EventRegistrationStatus.REGISTERED);

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.of(existing));

        EventRegistrationDto response = eventService.register(eventId, "sub", List.of());

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(EventRegistrationStatus.REGISTERED);
        verify(registrationRepository, never()).save(any(EventRegistration.class));
        verifyNoInteractions(questionnaireService, autoChallengeTriggerPublisher);
    }

    @Test
    void unregisterPromotesFirstWaitlistedUserWhenARegisteredSeatIsFreed() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        Event event = event(eventId);
        event.setCapacity(1);
        event.setWaitlistEnabled(true);

        EventRegistration registration = registration(event, user, EventRegistrationStatus.REGISTERED);
        EventRegistration waitlisted = registration(event, user(UUID.randomUUID()), EventRegistrationStatus.WAITLISTED);

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.of(registration));
        when(registrationRepository.findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(
                eventId, EventRegistrationStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        eventService.unregister(eventId, "sub");

        assertThat(waitlisted.getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
        verify(registrationRepository).delete(registration);
        verify(registrationRepository).save(waitlisted);
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        Instant startsAt = Instant.parse("2026-09-01T10:00:00Z");
        EventRequest request = new EventRequest(
                "Tournoi",
                "Description",
                null,
                null,
                EventSource.TOURNAMENT,
                startsAt,
                startsAt.minusSeconds(60),
                null,
                false,
                null,
                false,
                null,
                Set.of(),
                Set.of()
        );

        assertThatThrownBy(() -> eventService.createEvent(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La date de fin doit être postérieure à la date de début.");
    }

    @Test
    void uploadCoverStoresImageAndDeletesPreviousManagedCover() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        event.setCoverImageUrl("https://s3.example.com/insastronaute-public/events/" + eventId + "/cover/old.png");
        event.setCoverImageObjectKey("events/" + eventId + "/cover/old.png");

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "application/octet-stream",
                pngBytes()
        );

        var response = eventService.uploadCover(eventId, file);

        assertThat(response.coverImageUrl()).contains("/insastronaute-public/events/" + eventId + "/cover/");
        assertThat(event.getCoverImageObjectKey()).startsWith("events/" + eventId + "/cover/");
        assertThat(event.getCoverImageObjectKey()).endsWith("-cover.png");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(putCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(putCaptor.getValue().key()).startsWith("events/" + eventId + "/cover/");
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(deleteCaptor.getValue().key()).isEqualTo("events/" + eventId + "/cover/old.png");
    }

    @Test
    void updateEventCanSwitchCoverToExternalUrlAndDeletesPreviousManagedCover() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        event.setCoverImageUrl("https://s3.example.com/insastronaute-public/events/" + eventId + "/cover/old.png");
        event.setCoverImageObjectKey("events/" + eventId + "/cover/old.png");

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = eventService.updateEvent(eventId, eventRequest(" https://cdn.example.com/event-cover.jpg "));

        assertThat(response.coverImageUrl()).isEqualTo("https://cdn.example.com/event-cover.jpg");
        assertThat(event.getCoverImageObjectKey()).isNull();

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().key()).isEqualTo("events/" + eventId + "/cover/old.png");
    }

    @Test
    void updateEventKeepsManagedCoverWhenUrlIsUnchanged() {
        UUID eventId = UUID.randomUUID();
        String coverUrl = "https://s3.example.com/insastronaute-public/events/" + eventId + "/cover/current.png";
        Event event = event(eventId);
        event.setCoverImageUrl(coverUrl);
        event.setCoverImageObjectKey("events/" + eventId + "/cover/current.png");

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = eventService.updateEvent(eventId, eventRequest(coverUrl));

        assertThat(response.coverImageUrl()).isEqualTo(coverUrl);
        assertThat(event.getCoverImageObjectKey()).isEqualTo("events/" + eventId + "/cover/current.png");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void updateEventSynchronizesReservationSlotSnapshots() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        Instant startsAt = Instant.parse("2026-09-01T18:00:00Z");
        Instant endsAt = Instant.parse("2026-09-01T22:00:00Z");
        EventRequest request = new EventRequest(
                "Soiree modifiee", "Description", "Nouveau bar", null,
                EventSource.GLOBAL, startsAt, endsAt, null, false, null, false,
                null, Set.of(), Set.of()
        );
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        eventService.updateEvent(eventId, request);

        verify(reservationSlotRepository)
                .updateEventSnapshots(eventId, startsAt, endsAt, "Nouveau bar");
        verify(reservationSlotRepository, never()).deactivateByEventId(eventId);
    }

    @Test
    void updateEventPreservesSnapshotsThenDeactivatesSlotsWhenPickupDataBecomesInvalid() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        Instant startsAt = Instant.parse("2026-09-01T18:00:00Z");
        EventRequest request = new EventRequest(
                "Soiree modifiee", "Description", "Nouveau bar", null,
                EventSource.GLOBAL, startsAt, startsAt, null, false, null, false,
                null, Set.of(), Set.of()
        );
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        eventService.updateEvent(eventId, request);

        verify(reservationSlotRepository, never())
                .updateEventSnapshots(any(), any(), any(), any());
        verify(reservationSlotRepository).deactivateByEventId(eventId);
    }

    @Test
    void updateEventRejectsInvalidCoverUrl() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.updateEvent(eventId, eventRequest("ftp://cdn.example.com/event-cover.jpg")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'URL de couverture doit être une URL HTTP ou HTTPS.");
    }

    @Test
    void listPlayerEventsExposesAcceptedFriendsRegisteredToEachEvent() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        Event event = event(eventId);
        AppUser marie = friend("Marie");
        AppUser paul = friend("Paul");
        List<UUID> friendIds = List.of(marie.getId(), paul.getId());

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(List.of(event));
        when(friendshipRepository.findAcceptedFriendIds(userId)).thenReturn(friendIds);
        when(registrationRepository.findByEventIdInAndUserIdIn(List.of(eventId), friendIds)).thenReturn(List.of(
                registration(event, paul, EventRegistrationStatus.WAITLISTED),
                registration(event, marie, EventRegistrationStatus.REGISTERED)
        ));

        List<EventDto> events = eventService.listPlayerEvents("sub");

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().registeredFriends())
                .extracting(EventFriendDto::userId, EventFriendDto::displayName, EventFriendDto::status)
                .containsExactly(
                        tuple(marie.getId(), "Marie", EventRegistrationStatus.REGISTERED),
                        tuple(paul.getId(), "Paul", EventRegistrationStatus.WAITLISTED)
                );
    }

    @Test
    void listPlayerEventsSkipsRegistrationLookupWhenUserHasNoAcceptedFriend() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(List.of(event(UUID.randomUUID())));
        when(friendshipRepository.findAcceptedFriendIds(userId)).thenReturn(List.of());

        List<EventDto> events = eventService.listPlayerEvents("sub");

        assertThat(events.getFirst().registeredFriends()).isEmpty();
        verify(registrationRepository, never()).findByEventIdInAndUserIdIn(any(), any());
    }

    @Test
    void adminEventListingDoesNotExposeFriendRegistrations() {
        when(eventRepository.findAllByOrderByStartsAtAscCreatedAtDesc()).thenReturn(List.of(event(UUID.randomUUID())));

        List<EventDto> events = eventService.listAdminEvents();

        assertThat(events.getFirst().registeredFriends()).isEmpty();
        verifyNoInteractions(friendshipRepository);
    }

    // ── Retirer une inscription à la main ────────────────────────────────────

    private AppUser someone() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("sub-" + UUID.randomUUID());
        user.setDisplayName("Inscrit");
        return user;
    }

    /**
     * La place libérée est aussitôt reprise.
     *
     * <p>Supprimer la ligne sans ce repêchage laisserait un siège vide pendant que
     * quelqu'un attend, et personne ne saurait qu'il s'est libéré.</p>
     */
    @Test
    void removingAConfirmedRegistrationPromotesTheFirstWaitlisted() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        event.setCapacity(30);
        event.setWaitlistEnabled(true);
        AppUser removed = someone();
        EventRegistration waiting = registration(event, someone(), EventRegistrationStatus.WAITLISTED);

        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndUserId(eventId, removed.getId()))
                .thenReturn(Optional.of(registration(event, removed, EventRegistrationStatus.REGISTERED)));
        when(registrationRepository.findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(
                eventId, EventRegistrationStatus.WAITLISTED)).thenReturn(Optional.of(waiting));

        eventService.removeRegistration(eventId, removed.getId());

        assertThat(waiting.getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
        verify(registrationRepository).delete(any(EventRegistration.class));
    }

    /**
     * Retirer quelqu'un de la liste d'attente ne libère rien.
     *
     * <p>Promouvoir dans ce cas donnerait une place qui n'existe pas : l'événement est
     * plein, et il le reste.</p>
     */
    @Test
    void removingAWaitlistedRegistrationPromotesNobody() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        event.setCapacity(30);
        event.setWaitlistEnabled(true);
        AppUser removed = someone();

        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndUserId(eventId, removed.getId()))
                .thenReturn(Optional.of(registration(event, removed, EventRegistrationStatus.WAITLISTED)));

        eventService.removeRegistration(eventId, removed.getId());

        verify(registrationRepository).delete(any(EventRegistration.class));
        verify(registrationRepository, never())
                .findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(any(), any());
    }

    @Test
    void removingSomeoneWhoIsNotRegisteredIsNotFound() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event(eventId)));
        when(registrationRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.removeRegistration(eventId, userId))
                .isInstanceOf(NotFoundException.class);

        verify(registrationRepository, never()).delete(any(EventRegistration.class));
    }

    // ── Inscriptions fermées au staff ────────────────────────────────────────

    @Test
    void staffCannotTakeASeatWhenRegistrationIsClosedToThem() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        AppUser staffMember = someone();

        when(userRepository.findByKeycloakSub(staffMember.getKeycloakSub())).thenReturn(Optional.of(staffMember));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(eventSettings.staffRegistrationBlocked()).thenReturn(true);
        when(realmRoleDirectory.isKnown()).thenReturn(true);
        when(realmRoleDirectory.isStaff(staffMember.getKeycloakSub())).thenReturn(true);

        assertThatThrownBy(() -> eventService.register(eventId, staffMember.getKeycloakSub()))
                .isInstanceOf(ConflictException.class);

        verify(registrationRepository, never()).save(any());
    }

    /**
     * Annuaire injoignable : on ne ferme pas les inscriptions à tout le monde.
     *
     * <p>Un jour d'annonce, refuser sur une panne de Keycloak bloquerait trois cents
     * étudiants ; un membre du staff passé entre les mailles se retire à la main.</p>
     */
    @Test
    void aKeycloakOutageDoesNotCloseRegistrationsForEveryone() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        AppUser player = someone();

        when(userRepository.findByKeycloakSub(player.getKeycloakSub())).thenReturn(Optional.of(player));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(eventSettings.staffRegistrationBlocked()).thenReturn(true);
        when(realmRoleDirectory.isKnown()).thenReturn(false);

        // Pas d'exception : le geste continue son chemin normal.
        assertThatThrownBy(() -> eventService.register(eventId, player.getKeycloakSub()))
                .isNotInstanceOf(ConflictException.class);
    }

    private static Event event(UUID id) {
        Event event = new Event();
        event.setId(id);
        event.setTitle("Tournoi");
        event.setShortDescription("Description");
        event.setSource(EventSource.TOURNAMENT);
        event.setStartsAt(Instant.now().plusSeconds(3600));
        event.setPublishedAt(Instant.now().minusSeconds(60));
        event.setRegistrationEnabled(true);
        return event;
    }

    private static EventRequest eventRequest(String coverImageUrl) {
        return new EventRequest(
                "Tournoi",
                "Description",
                null,
                coverImageUrl,
                EventSource.TOURNAMENT,
                Instant.parse("2026-09-01T10:00:00Z"),
                null,
                null,
                false,
                null,
                false,
                null,
                Set.of(),
                Set.of()
        );
    }

    private static S3Properties s3Properties() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");
        s3Properties.setPublicEndpoint("https://s3.example.com");
        return s3Properties;
    }

    /**
     * Le departement force par le voyage dans le temps doit gouverner la liste
     * des events.
     *
     * <p>Il ne le faisait pas, et c'est ce qui a fait conclure que le voyage
     * dans le temps « ne marchait pas » : le departement force n'existait que
     * dans {@code UserSynchronizationService}, par lequel cette route ne passe
     * pas - elle lit l'utilisateur en base et prenait donc son vrai
     * departement, sans rien dire.</p>
     */
    @Test
    void aForcedDepartmentDrivesTheEventsAPlayerSees() {
        UUID realDepartment = UUID.randomUUID();
        UUID forcedDepartment = UUID.randomUUID();
        AppUser user = user(UUID.randomUUID());
        user.setDepartment(realDepartment);
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(List.of());
        TimeTravelContext.setForcedDepartmentId(forcedDepartment);

        eventService.listPlayerEvents("sub");

        verify(eventRepository).findVisibleForPlayer(eq(forcedDepartment), any());
    }

    /** Sans voyage, c'est le vrai departement, evidemment - et c'est a verifier. */
    @Test
    void withoutTimeTravelTheRealDepartmentIsUsed() {
        UUID realDepartment = UUID.randomUUID();
        AppUser user = user(UUID.randomUUID());
        user.setDepartment(realDepartment);
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(List.of());

        eventService.listPlayerEvents("sub");

        verify(eventRepository).findVisibleForPlayer(eq(realDepartment), any());
    }

    /**
     * Un apprenti est rattache a sa filiere partout, sauf ici.
     *
     * <p>Le ciblage PERF est exclusif : sa liste est celle de PERF, pas celle de son
     * departement parent. C'est la seule chose qui distingue son parcours du
     * parcours ordinaire.</p>
     */
    @Test
    void anApprenticeSeesThePerfEventsInsteadOfThoseOfTheirParentDepartment() {
        UUID parentDepartment = UUID.randomUUID();
        UUID perfDepartment = UUID.randomUUID();
        AppUser user = user(UUID.randomUUID());
        user.setDepartment(parentDepartment);
        user.setPerf(true);
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(departmentRepository.findFirstByVirtualTrue()).thenReturn(Optional.of(department(perfDepartment)));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(List.of());

        eventService.listPlayerEvents("sub");

        verify(eventRepository).findVisibleForPlayer(eq(perfDepartment), any());
    }

    /**
     * Le verrou ne doit tenir que ce qui en a besoin.
     *
     * <p>La construction de la reponse - amis inscrits, nombre de questions, statut,
     * decomptes - se faisait dans la meme transaction que la prise de place, donc
     * derriere le meme verrou pessimiste, et trois cents personnes la payaient chacune
     * a leur tour. Ce test epingle la separation : rien de ce qui sert a repondre ne
     * doit s'executer tant que la place n'est pas acquise.</p>
     */
    @Test
    void theResponseIsBuiltAfterTheSeatIsTakenNotUnderTheLock() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        Event event = event(eventId);

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.save(any(EventRegistration.class))).thenAnswer(invocation -> {
            EventRegistration registration = invocation.getArgument(0);
            registration.setId(UUID.randomUUID());
            // Au moment ou la place est prise, rien de la reponse n'a encore ete lu.
            verify(friendshipRepository, never()).findAcceptedFriendIds(any());
            verify(questionRepository, never()).countByEventId(any());
            return registration;
        });

        eventService.register(eventId, "sub");

        // Et apres, si : la reponse a bien ete construite.
        verify(friendshipRepository).findAcceptedFriendIds(userId);
    }

    /**
     * Le cout d'une page ne doit pas dependre du nombre d'evenements.
     *
     * <p>Chaque ligne coutait trois requetes - statut du joueur, inscrits, liste
     * d'attente - soit cent vingt requetes pour quarante evenements, par joueur et par
     * ouverture. C'est la troisieme fois que cette linearite apparait dans ce fichier :
     * un commentaire du service se felicite de l'avoir supprimee pour les
     * questionnaires, et elle etait revenue deux lignes plus bas. D'ou ce test, qui
     * mesure au lieu de faire confiance.</p>
     */
    @Test
    void aPageOfEventsCostsTheSameWhateverItsLength() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        user.setDepartment(UUID.randomUUID());
        List<Event> events = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> event(UUID.randomUUID()))
                .toList();

        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(eventRepository.findVisibleForPlayer(any(), any())).thenReturn(events);

        List<EventDto> page = eventService.listPlayerEvents("sub");

        assertThat(page).hasSize(12);
        // Groupees : une fois pour la page, quel que soit le nombre de lignes.
        verify(registrationRepository, times(1)).statusCountsByEventIds(any());
        verify(registrationRepository, times(1)).findByUserIdAndEventIdIn(eq(userId), any());
        // Et surtout, plus rien par evenement.
        verify(registrationRepository, never()).countByEventIdAndStatus(any(), any());
        verify(registrationRepository, never()).findByEventIdAndUserId(any(), any());
    }

    /** Un evenement sans departement cible s'adresse a tout le monde : personne n'est dehors. */
    @Test
    void anEventTargetingNobodyInParticularLeavesNobodyOut() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));

        assertThat(eventService.getOutOfScopeRegistrants(eventId)).isEmpty();
        verify(registrationRepository, never()).findByEventIdOrderByStatusAscCreatedAtAsc(any());
    }

    /**
     * Retrecir le perimetre ne desinscrit personne de lui-meme, mais l'incoherence doit
     * se voir : ces places comptent toujours dans le quota alors que leurs titulaires ne
     * voient plus l'evenement.
     */
    @Test
    void narrowingTheDepartmentsShowsWhoNoLongerBelongs() {
        UUID eventId = UUID.randomUUID();
        UUID targeted = UUID.randomUUID();
        UUID dropped = UUID.randomUUID();
        Event event = event(eventId);
        event.setTargetDepartments(java.util.Set.of(department(targeted)));

        AppUser stays = user(UUID.randomUUID());
        stays.setDepartment(targeted);
        AppUser leaves = user(UUID.randomUUID());
        leaves.setDepartment(dropped);
        AppUser noDepartment = user(UUID.randomUUID());

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId)).thenReturn(List.of(
                registration(event, stays, EventRegistrationStatus.REGISTERED),
                registration(event, leaves, EventRegistrationStatus.REGISTERED),
                registration(event, noDepartment, EventRegistrationStatus.REGISTERED)));

        List<EventRegistrantDto> outOfScope = eventService.getOutOfScopeRegistrants(eventId);

        // Celui sans departement voit tous les evenements -- findVisibleForPlayer le dit -
        // donc il n'est pas hors perimetre.
        assertThat(outOfScope).extracting(EventRegistrantDto::userId).containsExactly(leaves.getId());
    }

    /**
     * La liste des inscrits distingue les apprentis, sinon l'organisateur ne les voit pas.
     *
     * <p>Un apprenti porte le departement de sa filiere : sur un tirage d'evenement PERF,
     * tout le monde semble etre en GC ou en MECA et la liste ne dit plus a qui elle
     * s'adresse.</p>
     */
    @Test
    void theRegistrantListSaysWhoIsAnApprentice() {
        UUID eventId = UUID.randomUUID();
        UUID parentDepartment = UUID.randomUUID();
        Event event = event(eventId);

        AppUser apprentice = user(UUID.randomUUID());
        apprentice.setDepartment(parentDepartment);
        apprentice.setPerf(true);
        AppUser classmate = user(UUID.randomUUID());
        classmate.setDepartment(parentDepartment);

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId)).thenReturn(List.of(
                registration(event, apprentice, EventRegistrationStatus.REGISTERED),
                registration(event, classmate, EventRegistrationStatus.REGISTERED)));

        List<EventRegistrantDto> registrants = eventService.getRegistrants(eventId);

        assertThat(registrants)
                .extracting(EventRegistrantDto::userId, EventRegistrantDto::departmentId, EventRegistrantDto::perf)
                .containsExactly(
                        tuple(apprentice.getId(), parentDepartment, true),
                        tuple(classmate.getId(), parentDepartment, false));
    }

    /**
     * Le perimetre se lit avec la meme regle que la visibilite, sinon il desinscrit.
     *
     * <p>Un apprenti porte le departement de sa filiere : lu naivement, il est hors
     * perimetre de l'evenement PERF auquel il vient de s'inscrire, et le premier
     * reciblage le met dehors sans que personne ne le voie.</p>
     */
    @Test
    void anApprenticeBelongsToAPerfEventAndNotToOneTargetingTheirParentDepartment() {
        UUID eventId = UUID.randomUUID();
        UUID perfDepartment = UUID.randomUUID();
        UUID parentDepartment = UUID.randomUUID();
        Event event = event(eventId);
        event.setTargetDepartments(java.util.Set.of(department(perfDepartment)));

        AppUser apprentice = user(UUID.randomUUID());
        apprentice.setDepartment(parentDepartment);
        apprentice.setPerf(true);
        AppUser classmate = user(UUID.randomUUID());
        classmate.setDepartment(parentDepartment);

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(departmentRepository.findFirstByVirtualTrue()).thenReturn(Optional.of(department(perfDepartment)));
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId)).thenReturn(List.of(
                registration(event, apprentice, EventRegistrationStatus.REGISTERED),
                registration(event, classmate, EventRegistrationStatus.REGISTERED)));

        List<EventRegistrantDto> outOfScope = eventService.getOutOfScopeRegistrants(eventId);

        assertThat(outOfScope).extracting(EventRegistrantDto::userId).containsExactly(classmate.getId());
    }

    /** Le retrait previent chacun, libere les places et promeut autant de fois. */
    @Test
    void removingThemNotifiesEachOneAndPromotesTheWaitlist() {
        UUID eventId = UUID.randomUUID();
        UUID targeted = UUID.randomUUID();
        Event event = event(eventId);
        event.setCapacity(10);
        event.setWaitlistEnabled(true);
        event.setTargetDepartments(java.util.Set.of(department(targeted)));

        AppUser first = user(UUID.randomUUID());
        first.setDepartment(UUID.randomUUID());
        AppUser second = user(UUID.randomUUID());
        second.setDepartment(UUID.randomUUID());

        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId)).thenReturn(List.of(
                registration(event, first, EventRegistrationStatus.REGISTERED),
                registration(event, second, EventRegistrationStatus.REGISTERED)));

        List<EventRegistrantDto> removed = eventService.removeOutOfScopeRegistrants(eventId);

        assertThat(removed).hasSize(2);
        verify(notificationService).notifyUser(eq(first.getId()), any(), any(), any(), any());
        verify(notificationService).notifyUser(eq(second.getId()), any(), any(), any(), any());
        // Deux places rendues, donc deux promotions tentees.
        verify(registrationRepository, times(2))
                .findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(eventId, EventRegistrationStatus.WAITLISTED);
    }

    /**
     * La suppression d'un evenement n'avertissait personne : les inscriptions partaient
     * en cascade par la cle etrangere et l'etudiant l'apprenait en ne retrouvant plus
     * l'evenement. La notification doit donc partir avant la suppression, sans quoi il
     * n'y a plus personne a prevenir.
     */
    @Test
    void deletingAnEventWarnsItsRegistrantsFirst() {
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId);
        AppUser registered = user(UUID.randomUUID());

        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId))
                .thenReturn(List.of(registration(event, registered, EventRegistrationStatus.REGISTERED)));

        eventService.deleteEvent(eventId);

        InOrder order = inOrder(notificationService, eventRepository);
        order.verify(notificationService).notifyUser(eq(registered.getId()), any(), any(), any(), any());
        order.verify(eventRepository).delete(event);
    }

    private static Department department(UUID id) {
        Department department = new Department();
        department.setId(id);
        department.setName("Departement " + id);
        department.setShortName("DEP");
        return department;
    }

    private static AppUser user(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub("sub");
        user.setEmail(id + "@example.com");
        return user;
    }

    private static AppUser friend(String displayName) {
        AppUser friend = user(UUID.randomUUID());
        friend.setDisplayName(displayName);
        return friend;
    }

    private static EventRegistration registration(Event event, AppUser user, EventRegistrationStatus status) {
        EventRegistration registration = new EventRegistration();
        registration.setId(UUID.randomUUID());
        registration.setEvent(event);
        registration.setUser(user);
        registration.setStatus(status);
        return registration;
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
                0, 0, 0, 13, 'I', 'H', 'D', 'R',
                0, 0, 0, 1, 0, 0, 0, 1,
                8, 6, 0, 0, 0, 31, 21, (byte) 196, (byte) 137,
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 174, 'B', 96, (byte) 130
        };
    }
}
