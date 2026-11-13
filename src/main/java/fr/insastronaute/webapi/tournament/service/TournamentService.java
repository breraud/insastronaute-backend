package fr.insastronaute.webapi.tournament.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import fr.insastronaute.webapi.event.domain.EventSource;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.tournament.domain.TournamentPointAward;
import fr.insastronaute.webapi.tournament.domain.TournamentSettlement;
import fr.insastronaute.webapi.tournament.domain.TournamentVictoryTier;
import fr.insastronaute.webapi.tournament.dto.AdminTournamentDetailDto;
import fr.insastronaute.webapi.tournament.dto.AdminTournamentSummaryDto;
import fr.insastronaute.webapi.tournament.dto.TournamentAttendanceEntryDto;
import fr.insastronaute.webapi.tournament.dto.TournamentAttendanceRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentParticipantDto;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementResultDto;
import fr.insastronaute.webapi.tournament.dto.TournamentVictoryTierDto;
import fr.insastronaute.webapi.tournament.dto.TournamentVictoryTierRequest;
import fr.insastronaute.webapi.tournament.repository.TournamentPointAwardRepository;
import fr.insastronaute.webapi.tournament.repository.TournamentSettlementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TournamentService.class);
    private static final String AWARD_SOURCE_TYPE = "TOURNAMENT";

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final TournamentSettlementRepository settlementRepository;
    private final TournamentPointAwardRepository awardRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    public TournamentService(
            EventRepository eventRepository,
            EventRegistrationRepository registrationRepository,
            TournamentSettlementRepository settlementRepository,
            TournamentPointAwardRepository awardRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.settlementRepository = settlementRepository;
        this.awardRepository = awardRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
    }

    @Transactional(readOnly = true)
    public List<AdminTournamentSummaryDto> listTournaments() {
        List<Event> events = eventRepository.findBySourceOrderByStartsAtDesc(EventSource.TOURNAMENT);
        if (events.isEmpty()) {
            return List.of();
        }

        List<UUID> eventIds = events.stream().map(Event::getId).toList();
        Map<UUID, StatusCounts> statusCounts = statusCounts(eventIds);
        Map<UUID, AttendanceCounts> attendanceCounts = attendanceCounts(eventIds);
        Map<UUID, TournamentSettlement> settlements = settlementRepository.findByEventIdIn(eventIds)
                .stream()
                .collect(Collectors.toMap(settlement -> settlement.getEvent().getId(), Function.identity()));
        Map<UUID, Long> awardedPoints = awardedPoints(eventIds);

        return events.stream()
                .map(event -> toSummary(
                        event,
                        statusCounts.getOrDefault(event.getId(), StatusCounts.EMPTY),
                        attendanceCounts.getOrDefault(event.getId(), AttendanceCounts.EMPTY),
                        settlements.get(event.getId()),
                        awardedPoints.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminTournamentDetailDto getTournament(UUID eventId) {
        return buildDetail(findTournament(eventId));
    }

    @Transactional
    public AdminTournamentDetailDto updateAttendance(UUID eventId, TournamentAttendanceRequest request) {
        Event event = findTournament(eventId);
        applyAttendance(eventId, request.entries());
        return buildDetail(event);
    }

    /**
     * Inscrit a la main un joueur qui ne l'a pas fait lui-meme.
     *
     * <p>C'est le geste de l'organisation sur place : le joueur est la, il va jouer. Les
     * regles qui encadrent l'inscription en ligne -- inscriptions fermees, places
     * comptees, departements cibles, staff tenu a l'ecart -- n'ont pas a le retenir. Il
     * entre donc toujours ferme, jamais en liste d'attente : quelqu'un qu'on fait jouer
     * n'attend pas qu'une place se libere.</p>
     *
     * <p>Deja en liste d'attente, il est confirme. Deja inscrit, rien ne bouge, et son
     * pointage est garde.</p>
     */
    @Transactional
    public AdminTournamentDetailDto registerParticipant(UUID eventId, UUID userId) {
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Tournoi introuvable."));
        requireTournament(event);
        AppUser user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));

        Optional<EventRegistration> existing = registrationRepository.findByEventIdAndUserId(eventId, userId);
        if (existing.isPresent()) {
            EventRegistration registration = existing.get();
            if (registration.getStatus() == EventRegistrationStatus.WAITLISTED) {
                registration.setStatus(EventRegistrationStatus.REGISTERED);
            }
            return buildDetail(event);
        }

        EventRegistration registration = new EventRegistration();
        registration.setEvent(event);
        registration.setUser(user);
        registration.setStatus(EventRegistrationStatus.REGISTERED);
        registrationRepository.save(registration);
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.EVENT_REGISTERED);
        return buildDetail(event);
    }

    /**
     * Grants participation and victory points, then notifies every player whose
     * balance moved. Safe to call repeatedly: the award ledger is compared with
     * the new outcome and only the difference is applied.
     */
    @Transactional
    public TournamentSettlementResultDto settle(UUID eventId, TournamentSettlementRequest request, String actorKeycloakSub) {
        // Serialises concurrent settlements of the same tournament.
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Tournoi introuvable."));
        requireTournament(event);

        AppUser actor = actorKeycloakSub == null
                ? null
                : userRepository.findByKeycloakSub(actorKeycloakSub).orElse(null);

        if (request.attendance() != null && !request.attendance().isEmpty()) {
            applyAttendance(eventId, request.attendance());
        }

        List<EventRegistration> registrations = registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId);
        Map<UUID, EventRegistration> registrationsByUserId = registrations.stream()
                .collect(Collectors.toMap(registration -> registration.getUser().getId(), Function.identity(), (a, b) -> a));

        int participationPoints = request.participationPoints() == null ? 0 : request.participationPoints();
        List<TournamentVictoryTierRequest> tierRequests = request.victoryTiers() == null ? List.of() : request.victoryTiers();
        validateTiers(tierRequests, registrationsByUserId);

        TournamentSettlement settlement = persistSettlement(event, participationPoints, tierRequests, actor);

        Map<UUID, TargetAward> targets = computeTargets(registrations, participationPoints, tierRequests);
        SettlementOutcome outcome = applyAwards(event, targets, registrationsByUserId);

        settlement.setSettledAt(TimeTravelContext.instantNow());
        settlementRepository.save(settlement);

        boolean notifyPlayers = request.notifyPlayers() == null || request.notifyPlayers();
        int notificationsSent = notifyPlayers ? notifyAwardedPlayers(event, outcome) : 0;

        // Attendance and podiums both feed achievements, and both are only true
        // once the settlement is committed.
        registrationsByUserId.keySet().forEach(userId ->
                autoChallengeTriggerPublisher.publish(userId, AutoChallengeTrigger.TOURNAMENT_SETTLED));

        return new TournamentSettlementResultDto(
                outcome.awardedPlayerCount(),
                outcome.pointsGranted(),
                outcome.pointsRevoked(),
                notificationsSent,
                buildDetail(event)
        );
    }

    /**
     * Rolls the tournament back: every granted point is taken off the players'
     * balances and the ledger is cleared. The check-in state is preserved.
     */
    @Transactional
    public AdminTournamentDetailDto cancelSettlement(UUID eventId) {
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Tournoi introuvable."));
        requireTournament(event);

        List<TournamentPointAward> awards = awardRepository.findByEventId(eventId);
        for (TournamentPointAward award : awards) {
            adjustUserPoints(award.getUser().getId(), -award.getTotalPoints());
        }
        awardRepository.deleteAll(awards);

        settlementRepository.findByEventId(eventId).ifPresent(settlement -> {
            settlement.setSettledAt(null);
            settlementRepository.save(settlement);
        });

        return buildDetail(findTournament(eventId));
    }

    // ---------------------------------------------------------------- helpers

    private void applyAttendance(UUID eventId, List<TournamentAttendanceEntryDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        List<EventRegistration> registrations = registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId);
        Map<UUID, EventRegistration> byUserId = registrations.stream()
                .collect(Collectors.toMap(registration -> registration.getUser().getId(), Function.identity(), (a, b) -> a));

        Instant now = TimeTravelContext.instantNow();
        List<EventRegistration> touched = new ArrayList<>();

        for (TournamentAttendanceEntryDto entry : entries) {
            EventRegistration registration = byUserId.get(entry.userId());
            if (registration == null) {
                throw new BadRequestException("Un joueur pointé n'est pas inscrit à ce tournoi.");
            }
            if (Objects.equals(registration.getAttended(), entry.attended())) {
                continue;
            }
            registration.setAttended(entry.attended());
            registration.setAttendanceMarkedAt(entry.attended() == null ? null : now);
            touched.add(registration);
        }

        if (!touched.isEmpty()) {
            registrationRepository.saveAll(touched);
        }
    }

    private void validateTiers(
            List<TournamentVictoryTierRequest> tierRequests,
            Map<UUID, EventRegistration> registrationsByUserId
    ) {
        Set<UUID> alreadyRewarded = new HashSet<>();

        for (TournamentVictoryTierRequest tier : tierRequests) {
            List<UUID> memberUserIds = tier.memberUserIds() == null ? List.of() : tier.memberUserIds();
            if (memberUserIds.isEmpty()) {
                throw new BadRequestException("Le palier « " + tier.label() + " » ne désigne aucun vainqueur.");
            }

            for (UUID userId : memberUserIds) {
                EventRegistration registration = registrationsByUserId.get(userId);
                if (registration == null) {
                    throw new BadRequestException("Un vainqueur désigné n'est pas inscrit à ce tournoi.");
                }
                if (!Boolean.TRUE.equals(registration.getAttended())) {
                    throw new BadRequestException(
                            "Un vainqueur doit être marqué présent : « " + displayName(registration.getUser()) + " » ne l'est pas."
                    );
                }
                if (!alreadyRewarded.add(userId)) {
                    throw new BadRequestException(
                            "« " + displayName(registration.getUser()) + " » apparaît dans plusieurs paliers de victoire."
                    );
                }
            }
        }
    }

    private TournamentSettlement persistSettlement(
            Event event,
            int participationPoints,
            List<TournamentVictoryTierRequest> tierRequests,
            AppUser actor
    ) {
        TournamentSettlement settlement = settlementRepository.findByEventId(event.getId())
                .orElseGet(() -> {
                    TournamentSettlement created = new TournamentSettlement();
                    created.setEvent(event);
                    return created;
                });

        settlement.setParticipationPoints(participationPoints);
        settlement.setSettledByUserId(actor == null ? null : actor.getId());

        // Tiers are fully replaced on every settlement, so removing a podium
        // step in the UI removes it here too.
        settlement.getTiers().clear();
        int position = 0;
        for (TournamentVictoryTierRequest tierRequest : tierRequests) {
            TournamentVictoryTier tier = new TournamentVictoryTier();
            tier.setSettlement(settlement);
            tier.setPosition(position++);
            tier.setLabel(tierRequest.label().trim());
            tier.setPoints(tierRequest.points() == null ? 0 : tierRequest.points());
            tier.setTeamName(blankToNull(tierRequest.teamName()));
            tier.setMemberUserIds(new LinkedHashSet<>(tierRequest.memberUserIds()));
            settlement.getTiers().add(tier);
        }

        return settlementRepository.save(settlement);
    }

    private Map<UUID, TargetAward> computeTargets(
            List<EventRegistration> registrations,
            int participationPoints,
            List<TournamentVictoryTierRequest> tierRequests
    ) {
        Map<UUID, TargetAward> targets = new LinkedHashMap<>();

        for (EventRegistration registration : registrations) {
            if (!Boolean.TRUE.equals(registration.getAttended())) {
                continue;
            }
            targets.put(
                    registration.getUser().getId(),
                    new TargetAward(participationPoints, 0, null, null)
            );
        }

        for (TournamentVictoryTierRequest tier : tierRequests) {
            int points = tier.points() == null ? 0 : tier.points();
            String label = tier.label().trim();
            String teamName = blankToNull(tier.teamName());

            for (UUID userId : tier.memberUserIds()) {
                TargetAward current = targets.get(userId);
                if (current == null) {
                    // validateTiers already guarantees attendance, so this is defensive only.
                    continue;
                }
                targets.put(userId, new TargetAward(current.participationPoints(), points, label, teamName));
            }
        }

        return targets;
    }

    private SettlementOutcome applyAwards(
            Event event,
            Map<UUID, TargetAward> targets,
            Map<UUID, EventRegistration> registrationsByUserId
    ) {
        Map<UUID, TournamentPointAward> existingByUserId = awardRepository.findByEventId(event.getId())
                .stream()
                .collect(Collectors.toMap(award -> award.getUser().getId(), Function.identity(), (a, b) -> a));

        Set<UUID> affectedUserIds = new LinkedHashSet<>();
        affectedUserIds.addAll(targets.keySet());
        affectedUserIds.addAll(existingByUserId.keySet());

        long granted = 0L;
        long revoked = 0L;
        List<AwardedPlayer> awardedPlayers = new ArrayList<>();

        for (UUID userId : affectedUserIds) {
            TargetAward target = targets.get(userId);
            TournamentPointAward existing = existingByUserId.get(userId);
            int previousTotal = existing == null ? 0 : existing.getTotalPoints();
            int newTotal = target == null ? 0 : target.total();
            int delta = newTotal - previousTotal;

            if (target == null) {
                if (existing != null) {
                    awardRepository.delete(existing);
                }
            } else {
                TournamentPointAward award = existing == null ? new TournamentPointAward() : existing;
                award.setEvent(event);
                award.setUser(resolveUser(userId, registrationsByUserId));
                award.setParticipationPoints(target.participationPoints());
                award.setVictoryPoints(target.victoryPoints());
                award.setTotalPoints(newTotal);
                award.setTierLabel(target.tierLabel());
                award.setTeamName(target.teamName());
                awardRepository.save(award);
            }

            if (delta == 0) {
                continue;
            }

            adjustUserPoints(userId, delta);
            if (delta > 0) {
                granted += delta;
            } else {
                revoked += -delta;
            }

            if (target != null && newTotal > 0) {
                awardedPlayers.add(new AwardedPlayer(userId, target, delta));
            }
        }

        return new SettlementOutcome(targets.size(), granted, revoked, awardedPlayers);
    }

    private AppUser resolveUser(UUID userId, Map<UUID, EventRegistration> registrationsByUserId) {
        EventRegistration registration = registrationsByUserId.get(userId);
        if (registration != null) {
            return registration.getUser();
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));
    }

    private void adjustUserPoints(UUID userId, int delta) {
        if (delta == 0) {
            return;
        }
        AppUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));
        long updated = Math.max(0L, user.getTournamentPoints() + delta);
        user.setTournamentPoints(updated);
        userRepository.save(user);
    }

    private int notifyAwardedPlayers(Event event, SettlementOutcome outcome) {
        int sent = 0;
        for (AwardedPlayer awarded : outcome.awardedPlayers()) {
            TargetAward target = awarded.target();
            try {
                notificationService.notifyUser(
                        awarded.userId(),
                        NotificationType.TOURNAMENT_POINTS_AWARDED,
                        "Points de tournoi reçus",
                        buildNotificationBody(event, target),
                        buildNotificationPayload(event, target)
                );
                sent++;
            } catch (RuntimeException exception) {
                // A failed notification must not roll back points that were granted.
                LOGGER.warn(
                        "Could not notify user '{}' about tournament '{}' points.",
                        awarded.userId(),
                        event.getId(),
                        exception
                );
            }
        }
        return sent;
    }

    private String buildNotificationBody(Event event, TargetAward target) {
        StringBuilder body = new StringBuilder();
        body.append("Tu as gagné ").append(target.total()).append(" point");
        if (target.total() > 1) {
            body.append('s');
        }
        body.append(" de tournoi pour « ").append(event.getTitle()).append(" »");

        if (target.victoryPoints() > 0 && target.tierLabel() != null) {
            body.append(" : ").append(target.participationPoints()).append(" de participation et ")
                    .append(target.victoryPoints()).append(" pour la place ").append(target.tierLabel());
            if (target.teamName() != null) {
                body.append(" avec ").append(target.teamName());
            }
        } else {
            body.append(" grâce à ta présence");
        }
        body.append('.');
        return body.toString();
    }

    private Map<String, Object> buildNotificationPayload(Event event, TargetAward target) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getId().toString());
        payload.put("eventTitle", event.getTitle());
        payload.put("participationPoints", target.participationPoints());
        payload.put("victoryPoints", target.victoryPoints());
        payload.put("totalPoints", target.total());
        if (target.tierLabel() != null) {
            payload.put("tierLabel", target.tierLabel());
        }
        if (target.teamName() != null) {
            payload.put("teamName", target.teamName());
        }
        payload.put("sourceType", AWARD_SOURCE_TYPE);
        return payload;
    }

    private AdminTournamentDetailDto buildDetail(Event event) {
        UUID eventId = event.getId();
        List<EventRegistration> registrations = registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId);
        Map<UUID, TournamentPointAward> awardsByUserId = awardRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(award -> award.getUser().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, String> departmentNames = departmentShortNames(registrations);
        TournamentSettlement settlement = settlementRepository.findByEventId(eventId).orElse(null);

        List<TournamentParticipantDto> participants = registrations.stream()
                .map(registration -> toParticipant(registration, awardsByUserId, departmentNames))
                .sorted(Comparator
                        .comparing(TournamentParticipantDto::status)
                        .thenComparing(
                                TournamentParticipantDto::displayName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        )
                        .thenComparing(TournamentParticipantDto::userId))
                .toList();

        List<TournamentVictoryTierDto> tiers = settlement == null
                ? List.of()
                : settlement.getTiers().stream()
                .sorted(Comparator.comparing(TournamentVictoryTier::getPosition))
                .map(tier -> new TournamentVictoryTierDto(
                        tier.getPosition(),
                        tier.getLabel(),
                        tier.getPoints(),
                        tier.getTeamName(),
                        List.copyOf(tier.getMemberUserIds())
                ))
                .toList();

        StatusCounts statusCounts = statusCounts(List.of(eventId))
                .getOrDefault(eventId, StatusCounts.EMPTY);
        AttendanceCounts attendanceCounts = attendanceCounts(List.of(eventId))
                .getOrDefault(eventId, AttendanceCounts.EMPTY);
        long awarded = awardsByUserId.values().stream().mapToLong(TournamentPointAward::getTotalPoints).sum();

        return new AdminTournamentDetailDto(
                toSummary(event, statusCounts, attendanceCounts, settlement, awarded),
                participants,
                tiers
        );
    }

    private TournamentParticipantDto toParticipant(
            EventRegistration registration,
            Map<UUID, TournamentPointAward> awardsByUserId,
            Map<UUID, String> departmentNames
    ) {
        AppUser user = registration.getUser();
        TournamentPointAward award = awardsByUserId.get(user.getId());

        return new TournamentParticipantDto(
                registration.getId(),
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getDepartment(),
                user.getDepartment() == null ? null : departmentNames.get(user.getDepartment()),
                registration.getStatus(),
                registration.getAttended(),
                registration.getCreatedAt(),
                award == null ? 0 : award.getParticipationPoints(),
                award == null ? 0 : award.getVictoryPoints(),
                award == null ? 0 : award.getTotalPoints(),
                award == null ? null : award.getTierLabel(),
                award == null ? null : award.getTeamName()
        );
    }

    private AdminTournamentSummaryDto toSummary(
            Event event,
            StatusCounts statusCounts,
            AttendanceCounts attendanceCounts,
            TournamentSettlement settlement,
            long totalPointsAwarded
    ) {
        Instant reference = event.getEndsAt() == null ? event.getStartsAt() : event.getEndsAt();
        boolean hasEnded = reference != null && reference.isBefore(TimeTravelContext.instantNow());

        return new AdminTournamentSummaryDto(
                event.getId(),
                event.getTitle(),
                event.getLocation(),
                event.getCoverImageUrl(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getPublishedAt(),
                Boolean.TRUE.equals(event.getRegistrationEnabled()),
                hasEnded,
                statusCounts.registered(),
                statusCounts.waitlisted(),
                attendanceCounts.attended(),
                attendanceCounts.unmarked(),
                settlement != null && settlement.getSettledAt() != null,
                settlement == null ? null : settlement.getSettledAt(),
                settlement == null ? 0 : settlement.getParticipationPoints(),
                totalPointsAwarded
        );
    }

    private Map<UUID, String> departmentShortNames(List<EventRegistration> registrations) {
        Set<UUID> departmentIds = registrations.stream()
                .map(registration -> registration.getUser().getDepartment())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(departmentIds)
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getShortName));
    }

    private Map<UUID, StatusCounts> statusCounts(List<UUID> eventIds) {
        Map<UUID, long[]> accumulator = new HashMap<>();
        for (Object[] row : registrationRepository.statusCountsByEventIds(eventIds)) {
            UUID eventId = (UUID) row[0];
            EventRegistrationStatus status = (EventRegistrationStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] counts = accumulator.computeIfAbsent(eventId, key -> new long[2]);
            if (status == EventRegistrationStatus.REGISTERED) {
                counts[0] = count;
            } else {
                counts[1] = count;
            }
        }
        return accumulator.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new StatusCounts(entry.getValue()[0], entry.getValue()[1])
                ));
    }

    private Map<UUID, AttendanceCounts> attendanceCounts(List<UUID> eventIds) {
        return registrationRepository.attendanceCountsByEventIds(eventIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new AttendanceCounts(((Number) row[1]).longValue(), ((Number) row[2]).longValue())
                ));
    }

    private Map<UUID, Long> awardedPoints(List<UUID> eventIds) {
        return awardRepository.sumTotalPointsByEventIds(eventIds)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
    }

    private Event findTournament(UUID eventId) {
        Event event = eventRepository.findWithGraphById(eventId)
                .orElseThrow(() -> new NotFoundException("Tournoi introuvable."));
        requireTournament(event);
        return event;
    }

    private void requireTournament(Event event) {
        if (event.getSource() != EventSource.TOURNAMENT) {
            throw new BadRequestException("Cet événement n'est pas un tournoi.");
        }
    }

    private static String displayName(AppUser user) {
        String displayName = user.getDisplayName();
        return displayName == null || displayName.isBlank() ? user.getEmail() : displayName;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record StatusCounts(long registered, long waitlisted) {
        static final StatusCounts EMPTY = new StatusCounts(0L, 0L);
    }

    private record AttendanceCounts(long attended, long unmarked) {
        static final AttendanceCounts EMPTY = new AttendanceCounts(0L, 0L);
    }

    private record TargetAward(int participationPoints, int victoryPoints, String tierLabel, String teamName) {
        int total() {
            return participationPoints + victoryPoints;
        }
    }

    private record AwardedPlayer(UUID userId, TargetAward target, int delta) {
    }

    private record SettlementOutcome(
            int awardedPlayerCount,
            long pointsGranted,
            long pointsRevoked,
            List<AwardedPlayer> awardedPlayers
    ) {
    }
}
