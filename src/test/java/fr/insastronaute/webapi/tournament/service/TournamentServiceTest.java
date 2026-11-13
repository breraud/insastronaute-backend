package fr.insastronaute.webapi.tournament.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import fr.insastronaute.webapi.event.domain.EventSource;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.tournament.domain.TournamentPointAward;
import fr.insastronaute.webapi.tournament.domain.TournamentSettlement;
import fr.insastronaute.webapi.tournament.dto.TournamentAttendanceEntryDto;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementResultDto;
import fr.insastronaute.webapi.tournament.dto.TournamentVictoryTierRequest;
import fr.insastronaute.webapi.tournament.repository.TournamentPointAwardRepository;
import fr.insastronaute.webapi.tournament.repository.TournamentSettlementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRegistrationRepository registrationRepository;

    @Mock
    private TournamentSettlementRepository settlementRepository;

    @Mock
    private TournamentPointAwardRepository awardRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    private TournamentService tournamentService;

    private final UUID eventId = UUID.randomUUID();
    private final List<TournamentPointAward> awardStore = new ArrayList<>();
    private final Map<UUID, AppUser> usersById = new HashMap<>();

    @BeforeEach
    void setUp() {
        tournamentService = new TournamentService(
                eventRepository,
                registrationRepository,
                settlementRepository,
                awardRepository,
                departmentRepository,
                userRepository,
                notificationService,
                autoChallengeTriggerPublisher
        );

        awardStore.clear();
        usersById.clear();

        when(awardRepository.findByEventId(eventId)).thenReturn(awardStore);
        when(awardRepository.save(any(TournamentPointAward.class))).thenAnswer(invocation -> {
            TournamentPointAward award = invocation.getArgument(0);
            if (!awardStore.contains(award)) {
                awardStore.add(award);
            }
            return award;
        });
        doAnswer(invocation -> {
            awardStore.remove((TournamentPointAward) invocation.getArgument(0));
            return null;
        }).when(awardRepository).delete(any(TournamentPointAward.class));

        when(settlementRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(settlementRepository.save(any(TournamentSettlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(userRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get((UUID) invocation.getArgument(0))));
    }

    @Test
    void settleGrantsParticipationPointsOnlyToPlayersMarkedPresent() {
        AppUser present = player("Marie");
        AppUser absent = player("Paul");
        AppUser unmarked = player("Sarah");
        stubTournament(
                registration(present, true),
                registration(absent, false),
                registration(unmarked, null)
        );

        TournamentSettlementResultDto result = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, false),
                null
        );

        assertThat(present.getTournamentPoints()).isEqualTo(10L);
        assertThat(absent.getTournamentPoints()).isZero();
        assertThat(unmarked.getTournamentPoints()).isZero();
        assertThat(result.awardedPlayerCount()).isEqualTo(1);
        assertThat(result.pointsGranted()).isEqualTo(10L);
    }

    @Test
    void settleAddsVictoryPointsOnTopOfParticipationForEveryTeamMember() {
        AppUser first = player("Marie");
        AppUser teammate = player("Paul");
        AppUser second = player("Sarah");
        stubTournament(
                registration(first, true),
                registration(teammate, true),
                registration(second, true)
        );

        tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(
                        10,
                        null,
                        List.of(
                                new TournamentVictoryTierRequest("1er", 50, "Les Fusées", List.of(first.getId(), teammate.getId())),
                                new TournamentVictoryTierRequest("2e", 25, null, List.of(second.getId()))
                        ),
                        false
                ),
                null
        );

        assertThat(first.getTournamentPoints()).isEqualTo(60L);
        assertThat(teammate.getTournamentPoints()).isEqualTo(60L);
        assertThat(second.getTournamentPoints()).isEqualTo(35L);
    }

    @Test
    void settlingTwiceAppliesOnlyTheDifferenceInsteadOfStackingPoints() {
        AppUser present = player("Marie");
        stubTournament(registration(present, true));

        TournamentSettlementRequest request = new TournamentSettlementRequest(10, null, null, false);
        tournamentService.settle(eventId, request, null);
        assertThat(present.getTournamentPoints()).isEqualTo(10L);

        // Same outcome replayed: nothing moves.
        TournamentSettlementResultDto replay = tournamentService.settle(eventId, request, null);
        assertThat(present.getTournamentPoints()).isEqualTo(10L);
        assertThat(replay.pointsGranted()).isZero();
        assertThat(replay.pointsRevoked()).isZero();

        // Raised participation: only the top-up is applied.
        TournamentSettlementResultDto raised = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(25, null, null, false),
                null
        );
        assertThat(present.getTournamentPoints()).isEqualTo(25L);
        assertThat(raised.pointsGranted()).isEqualTo(15L);
    }

    @Test
    void settleTakesPointsBackWhenAPlayerIsNoLongerMarkedPresent() {
        AppUser player = player("Marie");
        EventRegistration registration = registration(player, true);
        stubTournament(registration);

        tournamentService.settle(eventId, new TournamentSettlementRequest(10, null, null, false), null);
        assertThat(player.getTournamentPoints()).isEqualTo(10L);

        registration.setAttended(false);
        TournamentSettlementResultDto corrected = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, false),
                null
        );

        assertThat(player.getTournamentPoints()).isZero();
        assertThat(corrected.pointsRevoked()).isEqualTo(10L);
        assertThat(awardStore).isEmpty();
    }

    @Test
    void settleRejectsAWinnerWhoWasNotMarkedPresent() {
        AppUser absentWinner = player("Marie");
        stubTournament(registration(absentWinner, false));

        assertThatThrownBy(() -> tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(
                        10,
                        null,
                        List.of(new TournamentVictoryTierRequest("1er", 50, null, List.of(absentWinner.getId()))),
                        false
                ),
                null
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("présent");

        assertThat(absentWinner.getTournamentPoints()).isZero();
    }

    @Test
    void settleRejectsAPlayerListedInTwoVictoryTiers() {
        AppUser player = player("Marie");
        stubTournament(registration(player, true));

        assertThatThrownBy(() -> tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(
                        10,
                        null,
                        List.of(
                                new TournamentVictoryTierRequest("1er", 50, null, List.of(player.getId())),
                                new TournamentVictoryTierRequest("2e", 25, null, List.of(player.getId()))
                        ),
                        false
                ),
                null
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("plusieurs paliers");
    }

    @Test
    void settleRejectsAttendanceForSomebodyWhoIsNotRegistered() {
        AppUser player = player("Marie");
        stubTournament(registration(player, null));

        assertThatThrownBy(() -> tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(
                        10,
                        List.of(new TournamentAttendanceEntryDto(UUID.randomUUID(), true)),
                        null,
                        false
                ),
                null
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pas inscrit");
    }

    @Test
    void settleRejectsAnEventThatIsNotATournament() {
        Event event = event();
        event.setSource(EventSource.GLOBAL);
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, false),
                null
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pas un tournoi");
    }

    @Test
    void settleNotifiesEveryPlayerWhoseBalanceMoved() {
        AppUser present = player("Marie");
        AppUser absent = player("Paul");
        stubTournament(registration(present, true), registration(absent, false));

        TournamentSettlementResultDto result = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, true),
                null
        );

        assertThat(result.notificationsSent()).isEqualTo(1);
        verify(notificationService).notifyUser(
                eq(present.getId()),
                eq(NotificationType.TOURNAMENT_POINTS_AWARDED),
                anyString(),
                anyString(),
                anyMap()
        );
        verify(notificationService, never()).notifyUser(
                eq(absent.getId()),
                any(NotificationType.class),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void settleSkipsNotificationsWhenTheOrganiserOptsOut() {
        AppUser present = player("Marie");
        stubTournament(registration(present, true));

        TournamentSettlementResultDto result = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, false),
                null
        );

        assertThat(result.notificationsSent()).isZero();
        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString(), anyMap());
    }

    @Test
    void aFailingNotificationDoesNotCostThePlayerTheirPoints() {
        AppUser present = player("Marie");
        stubTournament(registration(present, true));
        when(notificationService.notifyUser(any(), any(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("notification backend down"));

        TournamentSettlementResultDto result = tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(10, null, null, true),
                null
        );

        assertThat(present.getTournamentPoints()).isEqualTo(10L);
        assertThat(result.notificationsSent()).isZero();
    }

    @Test
    void cancellingASettlementTakesEveryGrantedPointBack() {
        AppUser first = player("Marie");
        AppUser second = player("Paul");
        stubTournament(registration(first, true), registration(second, true));

        tournamentService.settle(
                eventId,
                new TournamentSettlementRequest(
                        10,
                        null,
                        List.of(new TournamentVictoryTierRequest("1er", 40, null, List.of(first.getId()))),
                        false
                ),
                null
        );
        assertThat(first.getTournamentPoints()).isEqualTo(50L);
        assertThat(second.getTournamentPoints()).isEqualTo(10L);

        tournamentService.cancelSettlement(eventId);

        assertThat(first.getTournamentPoints()).isZero();
        assertThat(second.getTournamentPoints()).isZero();
        verify(awardRepository).deleteAll(any());
    }

    @Test
    void tournamentPointsNeverGoNegativeWhenAnAwardOutlivesABalanceReset() {
        AppUser player = player("Marie");
        EventRegistration registration = registration(player, true);
        stubTournament(registration);

        tournamentService.settle(eventId, new TournamentSettlementRequest(10, null, null, false), null);
        // Simulates an out-of-band reset of the balance before the correction.
        player.setTournamentPoints(0L);

        registration.setAttended(false);
        tournamentService.settle(eventId, new TournamentSettlementRequest(10, null, null, false), null);

        assertThat(player.getTournamentPoints()).isZero();
    }

    @Test
    void updateAttendanceStoresCheckInsWithoutGrantingAnyPoint() {
        AppUser player = player("Marie");
        EventRegistration registration = registration(player, null);
        stubTournament(registration);
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event()));

        tournamentService.updateAttendance(
                eventId,
                new fr.insastronaute.webapi.tournament.dto.TournamentAttendanceRequest(
                        List.of(new TournamentAttendanceEntryDto(player.getId(), true))
                )
        );

        assertThat(registration.getAttended()).isTrue();
        assertThat(registration.getAttendanceMarkedAt()).isNotNull();
        assertThat(player.getTournamentPoints()).isZero();
        verify(awardRepository, never()).save(any());
    }

    @Test
    void registerParticipantSignsUpAPlayerWhoNeverRegistered() {
        AppUser player = player("Nora");
        stubTournament();
        stubPlayerLookup(player);

        tournamentService.registerParticipant(eventId, player.getId());

        EventRegistration saved = savedRegistration();
        assertThat(saved.getUser()).isSameAs(player);
        assertThat(saved.getEvent().getId()).isEqualTo(eventId);
        assertThat(saved.getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
        verify(autoChallengeTriggerPublisher).publish(player.getId(), AutoChallengeTrigger.EVENT_REGISTERED);
    }

    @Test
    void registerParticipantPassesClosedRegistrationsAndAFullTournament() {
        AppUser player = player("Nora");
        Event event = stubTournament();
        event.setRegistrationEnabled(false);
        event.setCapacity(1);
        event.setWaitlistEnabled(true);
        when(registrationRepository.countByEventIdAndStatus(eventId, EventRegistrationStatus.REGISTERED)).thenReturn(1L);
        stubPlayerLookup(player);

        tournamentService.registerParticipant(eventId, player.getId());

        assertThat(savedRegistration().getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
    }

    @Test
    void registerParticipantConfirmsAWaitlistedPlayer() {
        AppUser player = player("Nora");
        EventRegistration waiting = registration(player, null);
        waiting.setStatus(EventRegistrationStatus.WAITLISTED);
        stubTournament(waiting);
        stubPlayerLookup(player);
        when(registrationRepository.findByEventIdAndUserId(eventId, player.getId())).thenReturn(Optional.of(waiting));

        tournamentService.registerParticipant(eventId, player.getId());

        assertThat(waiting.getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
        verify(autoChallengeTriggerPublisher, never()).publish(any(), any());
    }

    @Test
    void registerParticipantLeavesAConfirmedRegistrationAlone() {
        AppUser player = player("Nora");
        EventRegistration registered = registration(player, true);
        stubTournament(registered);
        stubPlayerLookup(player);
        when(registrationRepository.findByEventIdAndUserId(eventId, player.getId())).thenReturn(Optional.of(registered));

        tournamentService.registerParticipant(eventId, player.getId());

        assertThat(registered.getStatus()).isEqualTo(EventRegistrationStatus.REGISTERED);
        assertThat(registered.getAttended()).isTrue();
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void registerParticipantRefusesADeletedAccount() {
        AppUser player = player("Nora");
        player.setDeletedAt(Instant.parse("2026-09-20T10:00:00Z"));
        stubTournament();
        stubPlayerLookup(player);

        assertThatThrownBy(() -> tournamentService.registerParticipant(eventId, player.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void registerParticipantRefusesAnEventThatIsNotATournament() {
        AppUser player = player("Nora");
        Event event = stubTournament();
        event.setSource(EventSource.GLOBAL);
        stubPlayerLookup(player);

        assertThatThrownBy(() -> tournamentService.registerParticipant(eventId, player.getId()))
                .isInstanceOf(BadRequestException.class);
        verify(registrationRepository, never()).save(any());
    }

    // ------------------------------------------------------------- fixtures

    private void stubPlayerLookup(AppUser player) {
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(registrationRepository.findByEventIdAndUserId(eventId, player.getId())).thenReturn(Optional.empty());
        when(registrationRepository.save(any(EventRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EventRegistration savedRegistration() {
        ArgumentCaptor<EventRegistration> captor = ArgumentCaptor.forClass(EventRegistration.class);
        verify(registrationRepository).save(captor.capture());
        return captor.getValue();
    }

    private Event stubTournament(EventRegistration... registrations) {
        Event event = event();
        when(eventRepository.findWithLockById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.findWithGraphById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId))
                .thenReturn(new ArrayList<>(List.of(registrations)));
        return event;
    }

    private Event event() {
        Event event = new Event();
        event.setId(eventId);
        event.setTitle("Tournoi de volley");
        event.setShortDescription("Description");
        event.setSource(EventSource.TOURNAMENT);
        event.setStartsAt(Instant.parse("2026-09-12T09:00:00Z"));
        event.setRegistrationEnabled(true);
        return event;
    }

    private AppUser player(String displayName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(displayName.toLowerCase() + "@example.com");
        user.setDisplayName(displayName);
        user.setTournamentPoints(0L);
        usersById.put(user.getId(), user);
        return user;
    }

    private EventRegistration registration(AppUser user, Boolean attended) {
        EventRegistration registration = new EventRegistration();
        registration.setId(UUID.randomUUID());
        registration.setUser(user);
        registration.setStatus(EventRegistrationStatus.REGISTERED);
        registration.setAttended(attended);
        return registration;
    }
}
