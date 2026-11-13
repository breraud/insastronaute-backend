package fr.insastronaute.webapi.event.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventCategory;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import fr.insastronaute.webapi.event.dto.EventArticleDto;
import fr.insastronaute.webapi.event.dto.EventCategoryDto;
import fr.insastronaute.webapi.event.dto.EventCategoryRequest;
import fr.insastronaute.webapi.event.dto.EventDepartmentDto;
import fr.insastronaute.webapi.event.dto.EventDto;
import fr.insastronaute.webapi.event.dto.EventFriendDto;
import fr.insastronaute.webapi.event.dto.EventRegistrantDto;
import fr.insastronaute.webapi.event.dto.EventAnswerDto;
import fr.insastronaute.webapi.event.dto.EventRegistrationDto;
import fr.insastronaute.webapi.event.dto.EventRequest;
import fr.insastronaute.webapi.event.repository.EventCategoryRepository;
import fr.insastronaute.webapi.event.repository.EventQuestionRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.shop.repository.ShopReservationSlotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);
    private static final String EVENT_OBJECT_PREFIX = "events/";
    private static final String COVER_OBJECT_DIRECTORY = "cover/";
    private static final String DEFAULT_COVER_FILE_NAME = "cover";
    private static final long MAX_COVER_SIZE_BYTES = 15L * 1024L * 1024L;
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );
    private static final Map<String, String> DEFAULT_EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Tika TIKA = new Tika();
    private static final Comparator<EventRegistration> FRIEND_ORDER = Comparator
            .comparing(
                    (EventRegistration registration) -> registration.getUser().getDisplayName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            )
            .thenComparing(registration -> registration.getUser().getId());

    private final NotificationService notificationService;
    private final EventSettingsService eventSettings;
    private final RealmRoleDirectory realmRoleDirectory;
    private final TransactionTemplate seatTransactions;
    private final TransactionTemplate readTransactions;
    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventRegistrationRepository registrationRepository;
    private final DepartmentRepository departmentRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ShopReservationSlotRepository reservationSlotRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final EventQuestionnaireService questionnaireService;
    private final EventQuestionRepository questionRepository;

    public EventService(
            EventRepository eventRepository,
            EventCategoryRepository categoryRepository,
            EventRegistrationRepository registrationRepository,
            DepartmentRepository departmentRepository,
            ArticleRepository articleRepository,
            UserRepository userRepository,
            FriendshipRepository friendshipRepository,
            ShopReservationSlotRepository reservationSlotRepository,
            S3Client s3Client,
            S3Properties s3Properties,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            EventQuestionnaireService questionnaireService,
            EventQuestionRepository questionRepository,
            NotificationService notificationService,
            EventSettingsService eventSettings,
            RealmRoleDirectory realmRoleDirectory,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationService = notificationService;
        this.eventSettings = eventSettings;
        this.realmRoleDirectory = realmRoleDirectory;
        this.seatTransactions = new TransactionTemplate(transactionManager);
        this.readTransactions = new TransactionTemplate(transactionManager);
        this.readTransactions.setReadOnly(true);
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.registrationRepository = registrationRepository;
        this.departmentRepository = departmentRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.reservationSlotRepository = reservationSlotRepository;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.questionnaireService = questionnaireService;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<EventDto> listAdminEvents() {
        List<Event> events = eventRepository.findAllByOrderByStartsAtAscCreatedAtDesc();
        EventPageCounts counts = countsFor(events, null);
        Set<UUID> withQuestions = eventIdsWithQuestions(events);
        return events.stream()
                .map(event -> toDto(event, null, List.of(), withQuestions.contains(event.getId()), counts))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDto getAdminEvent(UUID eventId) {
        return toDto(findEvent(eventId), null);
    }

    @Transactional
    public EventDto createEvent(EventRequest request) {
        Event event = new Event();
        applyRequest(event, request);
        return toDto(eventRepository.save(event), null);
    }

    @Transactional
    public EventDto updateEvent(UUID eventId, EventRequest request) {
        Event event = findEvent(eventId);
        applyRequest(event, request);
        Event savedEvent = eventRepository.save(event);
        if (hasValidShopPickupData(savedEvent)) {
            reservationSlotRepository.updateEventSnapshots(
                    eventId,
                    savedEvent.getStartsAt(),
                    savedEvent.getEndsAt(),
                    savedEvent.getLocation()
            );
        } else {
            reservationSlotRepository.deactivateByEventId(eventId);
        }
        return toDto(savedEvent, null);
    }

    @Transactional
    public EventDto uploadCover(UUID eventId, MultipartFile file) {
        Event event = findEvent(eventId);
        validateCoverFile(file);

        String fileType = detectedFileType(file);
        String originalFileName = safeFileName(file.getOriginalFilename(), fileType);
        validateFileExtension(originalFileName, fileType);
        String oldObjectKey = event.getCoverImageObjectKey();
        String objectKey = EVENT_OBJECT_PREFIX + event.getId() + "/" + COVER_OBJECT_DIRECTORY + UUID.randomUUID() + "-" + originalFileName;

        putObject(objectKey, fileType, file);

        event.setCoverImageUrl(publicUrl(objectKey));
        event.setCoverImageObjectKey(objectKey);
        Event savedEvent = eventRepository.save(event);

        if (!objectKey.equals(oldObjectKey)) {
            deleteObjectQuietly(oldObjectKey);
        }

        return toDto(savedEvent, null);
    }

    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = findEvent(eventId);
        String coverImageObjectKey = event.getCoverImageObjectKey();
        // Avant la cascade, pas apres : event_registrations.event_id est en ON DELETE
        // CASCADE, donc les inscriptions disparaissent avec l'evenement et il n'y a
        // plus personne a prevenir une fois la ligne supprimee. Sans ce passage, un
        // etudiant apprenait l'annulation en ne retrouvant plus l'evenement.
        notifyRegistrations(
                registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId),
                event,
                NotificationType.EVENT_CANCELLED,
                "Événement annulé",
                "%s a été annulé. Ton inscription est retirée.");
        reservationSlotRepository.deactivateByEventId(eventId);
        reservationSlotRepository.flush();
        eventRepository.delete(event);
        eventRepository.flush();
        deleteObjectQuietly(coverImageObjectKey);
    }

    @Transactional(readOnly = true)
    public List<EventDto> listPlayerEvents(String keycloakSub) {
        AppUser user = currentUser(keycloakSub);
        UUID departmentId = TimeTravelContext.departmentIdOr(audienceDepartmentId(user));
        List<Event> events = eventRepository.findVisibleForPlayer(departmentId, TimeTravelContext.instantNow());
        Map<UUID, List<EventFriendDto>> friendsByEvent = registeredFriendsByEvent(events, user.getId());
        // Un seul appel pour toute la liste : compter question par question
        // rendrait la page linéaire en nombre d'événements.
        Set<UUID> withQuestions = eventIdsWithQuestions(events);

        EventPageCounts counts = countsFor(events, user.getId());
        return events.stream()
                .map(event -> toDto(event, user.getId(), registeredFriends(friendsByEvent, event),
                        withQuestions.contains(event.getId()), counts))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventRegistrationDto> getMyRegistrations(String keycloakSub) {
        AppUser user = currentUser(keycloakSub);
        List<EventRegistration> registrations = registrationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<UUID, List<EventFriendDto>> friendsByEvent = registeredFriendsByEvent(
                registrations.stream().map(EventRegistration::getEvent).toList(),
                user.getId()
        );

        List<Event> events = registrations.stream().map(EventRegistration::getEvent).toList();
        EventPageCounts counts = countsFor(events, user.getId());
        Set<UUID> withQuestions = eventIdsWithQuestions(events);
        return registrations.stream()
                .map(registration -> new EventRegistrationDto(
                        registration.getId(),
                        toDto(registration.getEvent(), user.getId(), registeredFriends(friendsByEvent, registration.getEvent()),
                                withQuestions.contains(registration.getEvent().getId()), counts),
                        registration.getStatus(),
                        registration.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventRegistrationDto> getMyWaitlist(String keycloakSub) {
        return getMyRegistrations(keycloakSub)
                .stream()
                .filter(registration -> registration.status() == EventRegistrationStatus.WAITLISTED)
                .toList();
    }

    public EventRegistrationDto register(UUID eventId, String keycloakSub) {
        return register(eventId, keycloakSub, null);
    }

    /**
     * Prendre une place, puis raconter ce qui a ete pris - en deux transactions,
     * deliberement.
     *
     * <p>Le verrou pessimiste sur la ligne de l'evenement serialise les inscriptions,
     * ce qui est exactement son role : deux personnes ne peuvent pas prendre la
     * derniere place. Mais il tient jusqu'au commit, et tout ce qui se trouve dans la
     * transaction attend derriere lui. La construction de la reponse s'y trouvait :
     * les amis inscrits, le nombre de questions, le statut du joueur, les deux
     * decomptes - six requetes qui ne protegent rien et que trois cents personnes
     * payaient chacune a leur tour.</p>
     *
     * <p>Mesure a l'appui : la campagne du 16 aout donne une latence exactement
     * proportionnelle au nombre de joueurs, a un peu moins de neuf millisecondes par
     * inscription, pour douze requetes sous verrou. Le systeme ne saturait pas, il
     * faisait la queue.</p>
     *
     * <p>Ce qui reste sous le verrou est ce qui en a besoin : le controle de double
     * inscription, le decompte de quota qui decide entre place et liste d'attente, et
     * l'insertion. Les reponses au questionnaire y restent aussi, parce qu'une reponse
     * refusee doit annuler l'inscription avec elle.</p>
     *
     * <p>Consequence a connaitre : la place est acquise des que la premiere
     * transaction valide. Si la seconde echouait - c'est une lecture, donc rare -
     * l'appelant verrait une erreur alors qu'il est inscrit, et le constaterait a la
     * lecture suivante. L'inverse, perdre une place pour un echec d'affichage, serait
     * plus grave.</p>
     */
    public EventRegistrationDto register(UUID eventId, String keycloakSub, List<EventAnswerDto> answers) {
        Seat seat = seatTransactions.execute(status -> takeSeat(eventId, keycloakSub, answers));
        return readTransactions.execute(status -> registrationDto(seat));
    }

    /**
     * Ce que la premiere transaction transmet a la seconde.
     *
     * <p>Tout ce qui decrit l'inscription voyage ici plutot que d'etre relu : la
     * seconde transaction n'a besoin que de l'evenement, et elle le charge avec son
     * graphe - article, categories, departements - ce que la construction du DTO
     * exigerait de toute facon, une requete au lieu de trois chargements paresseux.</p>
     */
    private record Seat(UUID registrationId, UUID userId, UUID eventId,
                        EventRegistrationStatus status, Instant createdAt) {}

    /**
     * Le staff prend-il encore des places ?
     *
     * <p>La question ne se pose qu'a annuaire connu. {@link RealmRoleDirectory#isKnown()}
     * distingue « personne n'est staff » de « on n'a pas pu demander » : refuser sur la
     * seconde fermerait les inscriptions a tout le monde pour une panne de Keycloak, un
     * jour d'annonce, alors qu'un membre du staff passe entre les mailles se retire a la
     * main.</p>
     */
    private void requireRegistrationOpenToStaff(AppUser user) {
        if (!eventSettings.staffRegistrationBlocked() || !realmRoleDirectory.isKnown()) {
            return;
        }
        if (realmRoleDirectory.isStaff(user.getKeycloakSub())) {
            throw new ConflictException("Les inscriptions sont réservées aux étudiants.");
        }
    }

    private Seat takeSeat(UUID eventId, String keycloakSub, List<EventAnswerDto> answers) {
        AppUser user = currentUser(keycloakSub);
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));

        if (!isVisibleToUser(event, user, TimeTravelContext.instantNow())) {
            throw new NotFoundException("Événement introuvable.");
        }
        Optional<EventRegistration> existing = registrationRepository.findByEventIdAndUserId(eventId, user.getId());
        if (existing.isPresent()) {
            EventRegistration registration = existing.get();
            return new Seat(registration.getId(), user.getId(), event.getId(),
                    registration.getStatus(), registration.getCreatedAt());
        }
        if (!Boolean.TRUE.equals(event.getRegistrationEnabled())) {
            throw new BadRequestException("Les inscriptions ne sont pas ouvertes pour cet événement.");
        }
        requireRegistrationOpenToStaff(user);

        EventRegistration registration = new EventRegistration();
        registration.setEvent(event);
        registration.setUser(user);
        registration.setStatus(nextRegistrationStatus(event));

        EventRegistration savedRegistration = registrationRepository.save(registration);
        // Answers are validated after the seat is taken but inside the same
        // transaction: a rejected answer rolls the registration back with it.
        questionnaireService.saveAnswers(savedRegistration, answers);
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.EVENT_REGISTERED);
        return new Seat(savedRegistration.getId(), user.getId(), event.getId(),
                savedRegistration.getStatus(), savedRegistration.getCreatedAt());
    }

    private EventRegistrationDto registrationDto(Seat seat) {
        Event event = eventRepository.findWithGraphById(seat.eventId())
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
        return new EventRegistrationDto(
                seat.registrationId(),
                toDto(event, seat.userId()),
                seat.status(),
                seat.createdAt());
    }

    @Transactional(readOnly = true)
    public List<EventAnswerDto> answersFor(UUID eventId, String keycloakSub) {
        AppUser user = currentUser(keycloakSub);
        return registrationRepository.findByEventIdAndUserId(eventId, user.getId())
                .map(registration -> questionnaireService.answersFor(registration.getId()))
                .orElseGet(List::of);
    }

    /**
     * Lets a registrant revise their answers without giving up their seat, which
     * unregistering and registering again would cost them once a quota is full.
     */
    @Transactional
    public List<EventAnswerDto> updateAnswers(UUID eventId, String keycloakSub, List<EventAnswerDto> answers) {
        AppUser user = currentUser(keycloakSub);
        EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new NotFoundException("Inscription introuvable."));
        if (!Boolean.TRUE.equals(registration.getEvent().getRegistrationEnabled())) {
            throw new BadRequestException("Les inscriptions sont fermées pour cet événement.");
        }

        questionnaireService.saveAnswers(registration, answers);
        return questionnaireService.answersFor(registration.getId());
    }

    @Transactional
    public void unregister(UUID eventId, String keycloakSub) {
        AppUser user = currentUser(keycloakSub);
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
        EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new NotFoundException("Inscription introuvable."));
        EventRegistrationStatus removedStatus = registration.getStatus();

        registrationRepository.delete(registration);
        if (removedStatus == EventRegistrationStatus.REGISTERED && Boolean.TRUE.equals(event.getWaitlistEnabled()) && hasLimitedCapacity(event)) {
            promoteNextWaitlisted(eventId);
        }
    }

    /**
     * Les inscrits qu'un retrecissement du perimetre laisse dehors.
     *
     * <p>Changer les departements cibles ne desinscrit personne, et c'est voulu :
     * chaque depart d'un inscrit promeut quelqu'un de la liste d'attente, donc une
     * case decochee par erreur redistribuerait definitivement qui vient - recocher ne
     * rendrait pas les places, elles seraient prises. Mais l'incoherence doit se voir :
     * ces inscriptions occupent toujours le quota alors que leurs titulaires ne voient
     * meme plus l'evenement.</p>
     *
     * <p>Un evenement sans departement cible ne laisse personne dehors, et un inscrit
     * sans departement non plus - {@code findVisibleForPlayer} rend tout visible a qui
     * n'en a pas, et le calcul suit cette regle plutot que d'en inventer une autre.</p>
     */
    @Transactional(readOnly = true)
    public List<EventRegistrantDto> getOutOfScopeRegistrants(UUID eventId) {
        Event event = findEvent(eventId);
        return outOfScopeRegistrations(event).stream().map(this::toRegistrantDto).toList();
    }

    /**
     * Desinscrit ceux que le perimetre laisse dehors, et le leur dit.
     *
     * <p>Geste explicite, et separe de la mise a jour de l'evenement : il supprime des
     * inscriptions et promeut la liste d'attente, ce qu'aucune case a cocher ne devrait
     * declencher au passage.</p>
     *
     * <p>Les hors-perimetre partent tous d'abord, ceux de la liste d'attente compris,
     * pour qu'une promotion ne rappelle pas quelqu'un qu'on vient d'ecarter.</p>
     */
    /**
     * Retirer une inscription, une seule, choisie a la main.
     *
     * <p>Meme mecanique que le retrait groupe hors perimetre, et pour les memes raisons :
     * l'evenement est verrouille le temps du geste, l'inscrit est prevenu, et la place
     * liberee est aussitot reprise par le premier de la liste d'attente. Supprimer la
     * ligne sans ce repechage laisserait un siege vide pendant que quelqu'un attend --
     * et personne ne saurait qu'il s'est libere.</p>
     *
     * <p>Seule une inscription confirmee libere un siege. Retirer quelqu'un de la liste
     * d'attente ne rend rien : promouvoir dans ce cas donnerait une place qui n'existe
     * pas.</p>
     */
    @Transactional
    public EventRegistrantDto removeRegistration(UUID eventId, UUID userId) {
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
        EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Cette personne n'est pas inscrite à cet événement."));

        EventRegistrantDto removed = toRegistrantDto(registration);
        boolean freedSeat = registration.getStatus() == EventRegistrationStatus.REGISTERED;

        notifyRegistrations(List.of(registration), event, NotificationType.EVENT_REGISTRATION_CANCELLED,
                "Inscription retirée",
                "Ton inscription à %s a été retirée par l'organisation.");
        registrationRepository.delete(registration);
        registrationRepository.flush();

        if (freedSeat && Boolean.TRUE.equals(event.getWaitlistEnabled()) && hasLimitedCapacity(event)) {
            promoteNextWaitlisted(eventId);
        }
        return removed;
    }

    @Transactional
    public List<EventRegistrantDto> removeOutOfScopeRegistrants(UUID eventId) {
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
        List<EventRegistration> removed = outOfScopeRegistrations(event);
        if (removed.isEmpty()) {
            return List.of();
        }

        List<EventRegistrantDto> dtos = removed.stream().map(this::toRegistrantDto).toList();
        long freedSeats = removed.stream()
                .filter(registration -> registration.getStatus() == EventRegistrationStatus.REGISTERED)
                .count();

        notifyRegistrations(removed, event, NotificationType.EVENT_REGISTRATION_CANCELLED,
                "Inscription retirée",
                "%s ne s'adresse plus à ton département. Ton inscription a été retirée.");
        registrationRepository.deleteAll(removed);
        registrationRepository.flush();

        if (Boolean.TRUE.equals(event.getWaitlistEnabled()) && hasLimitedCapacity(event)) {
            for (long seat = 0; seat < freedSeats; seat++) {
                promoteNextWaitlisted(eventId);
            }
        }
        return dtos;
    }

    private List<EventRegistration> outOfScopeRegistrations(Event event) {
        Set<UUID> targeted = event.getTargetDepartments().stream()
                .map(Department::getId)
                .collect(Collectors.toSet());
        if (targeted.isEmpty()) {
            return List.of();
        }
        List<EventRegistration> registrations =
                registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(event.getId());
        // Une seule lecture de l'audience pour toute la liste, et aucune si personne
        // n'est apprenti : le cas ordinaire ne paie pas la requete.
        UUID perfDepartmentId = registrations.stream().anyMatch(registration -> registration.getUser().isPerf())
                ? perfDepartmentId()
                : null;
        return registrations.stream()
                .filter(registration -> {
                    UUID department = audienceDepartmentId(registration.getUser(), perfDepartmentId);
                    return department != null && !targeted.contains(department);
                })
                .toList();
    }

    /**
     * Previent chaque inscrit, sans qu'un echec d'envoi n'emporte le geste.
     *
     * <p>Une notification qui ne part pas est facheuse ; une annulation d'evenement qui
     * echoue parce qu'une notification n'est pas partie l'est davantage.</p>
     */
    private void notifyRegistrations(
            List<EventRegistration> registrations, Event event, NotificationType type, String title, String bodyFormat) {
        for (EventRegistration registration : registrations) {
            try {
                notificationService.notifyUser(
                        registration.getUser().getId(),
                        type,
                        title,
                        bodyFormat.formatted(event.getTitle()),
                        Map.of("eventId", String.valueOf(event.getId()), "eventTitle", String.valueOf(event.getTitle()))
                );
            } catch (RuntimeException exception) {
                LOGGER.warn("Notification {} non envoyée à {} pour l'événement {}",
                        type, registration.getUser().getId(), event.getId(), exception);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<EventRegistrantDto> getRegistrants(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Événement introuvable.");
        }

        return registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId)
                .stream()
                .map(this::toRegistrantDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventRegistrantDto> getWaitlist(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Événement introuvable.");
        }

        return registrationRepository.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, EventRegistrationStatus.WAITLISTED)
                .stream()
                .map(this::toRegistrantDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventCategoryDto> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toCategoryDto)
                .toList();
    }

    @Transactional
    public EventCategoryDto createCategory(EventCategoryRequest request) {
        String name = normalizeName(request.name());
        String slug = slug(name);
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Cette catégorie existe déjà.");
        }

        EventCategory category = new EventCategory();
        category.setName(name);
        category.setSlug(slug);
        return toCategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public EventCategoryDto updateCategory(UUID categoryId, EventCategoryRequest request) {
        EventCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
        String name = normalizeName(request.name());
        String slug = slug(name);
        if (!category.getSlug().equals(slug) && categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Cette catégorie existe déjà.");
        }

        category.setName(name);
        category.setSlug(slug);
        return toCategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        EventCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
        categoryRepository.delete(category);
    }

    private void applyRequest(Event event, EventRequest request) {
        if (request == null) {
            throw new BadRequestException("Le contenu de l'événement est requis.");
        }
        if (request.endsAt() != null && request.endsAt().isBefore(request.startsAt())) {
            throw new BadRequestException("La date de fin doit être postérieure à la date de début.");
        }
        if (request.capacity() != null && request.capacity() < 0) {
            throw new BadRequestException("Le quota ne peut pas être négatif.");
        }

        event.setTitle(request.title().trim());
        // La colonne reste NOT NULL et recoit une chaine vide plutot qu'un null : les deux
        // fronts testent deja la valeur avant de l'afficher, mais le panneau d'administration
        // la `trim()` au chargement d'un evenement, et un null y leverait une erreur en
        // ouvrant le formulaire. Vide dit la meme chose sans rien casser.
        event.setShortDescription(request.shortDescription() == null ? "" : request.shortDescription().trim());
        event.setLocation(blankToNull(request.location()));
        applyCoverImageUrl(event, request.coverImageUrl());
        event.setSource(request.source());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setPublishedAt(request.publishedAt());
        event.setRegistrationEnabled(request.registrationEnabled());
        event.setCapacity(request.capacity());
        event.setWaitlistEnabled(request.waitlistEnabled());
        event.setArticle(findArticle(request.articleId()));
        event.setCategories(findCategories(request.categoryIds()));
        event.setTargetDepartments(findDepartments(request.targetDepartmentIds()));
    }

    private EventRegistrationStatus nextRegistrationStatus(Event event) {
        if (!hasLimitedCapacity(event)) {
            return EventRegistrationStatus.REGISTERED;
        }

        long registeredCount = registrationRepository.countByEventIdAndStatus(event.getId(), EventRegistrationStatus.REGISTERED);
        if (registeredCount < event.getCapacity()) {
            return EventRegistrationStatus.REGISTERED;
        }
        if (Boolean.TRUE.equals(event.getWaitlistEnabled())) {
            return EventRegistrationStatus.WAITLISTED;
        }

        throw new ConflictException("Le quota de cet événement est atteint.");
    }

    private void promoteNextWaitlisted(UUID eventId) {
        registrationRepository.findFirstByEventIdAndStatusOrderByCreatedAtAscIdAsc(
                        eventId,
                        EventRegistrationStatus.WAITLISTED
                )
                .ifPresent(registration -> {
                    registration.setStatus(EventRegistrationStatus.REGISTERED);
                    registrationRepository.save(registration);
                });
    }

    private boolean hasLimitedCapacity(Event event) {
        return event.getCapacity() != null && event.getCapacity() > 0;
    }

    private boolean hasValidShopPickupData(Event event) {
        return event.getStartsAt() != null
                && event.getEndsAt() != null
                && event.getEndsAt().isAfter(event.getStartsAt())
                && event.getLocation() != null
                && !event.getLocation().isBlank();
    }

    private void applyCoverImageUrl(Event event, String requestedCoverImageUrl) {
        String normalizedCoverImageUrl = blankToNull(requestedCoverImageUrl);
        validateCoverImageUrl(normalizedCoverImageUrl);
        String currentCoverImageUrl = blankToNull(event.getCoverImageUrl());

        if (Objects.equals(currentCoverImageUrl, normalizedCoverImageUrl)) {
            return;
        }

        String oldObjectKey = event.getCoverImageObjectKey();
        event.setCoverImageUrl(normalizedCoverImageUrl);
        event.setCoverImageObjectKey(null);
        deleteObjectQuietly(oldObjectKey);
    }

    private void validateCoverFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Un fichier de couverture est requis.");
        }

        if (file.getSize() > MAX_COVER_SIZE_BYTES) {
            throw new BadRequestException("L'image de couverture dépasse la taille maximale de 15 Mo.");
        }
    }

    private String detectedFileType(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            String detectedType = TIKA.detect(inputStream);
            String normalizedType = detectedType == null ? "" : detectedType.toLowerCase(Locale.ROOT);

            if (!ALLOWED_EXTENSIONS_BY_TYPE.containsKey(normalizedType)) {
                throw new BadRequestException("Format d'image de couverture non autorisé.");
            }

            return normalizedType;
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire l'image de couverture.");
        }
    }

    private void validateFileExtension(String fileName, String fileType) {
        String extension = extension(fileName);
        if (extension.isBlank() || !ALLOWED_EXTENSIONS_BY_TYPE.get(fileType).contains(extension)) {
            throw new BadRequestException("L'extension de l'image de couverture ne correspond pas à son format.");
        }
    }

    private void validateCoverImageUrl(String coverImageUrl) {
        if (coverImageUrl == null) {
            return;
        }

        try {
            URI uri = URI.create(coverImageUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new BadRequestException("L'URL de couverture doit être une URL HTTP ou HTTPS.");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("L'URL de couverture est invalide.");
        }
    }

    private void putObject(String objectKey, String fileType, MultipartFile file) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(s3Properties.getPublicBucket())
                            .key(objectKey)
                            .contentType(fileType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire l'image de couverture.");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Impossible d'enregistrer l'image de couverture.");
        }
    }

    private void deleteObjectQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not delete event cover object '{}' from public S3 bucket '{}'.",
                    objectKey,
                    s3Properties.getPublicBucket(),
                    exception
            );
        }
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier une image de couverture.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }

    private String safeFileName(String fileName, String fileType) {
        String value = fileName == null || fileName.isBlank()
                ? DEFAULT_COVER_FILE_NAME + "." + DEFAULT_EXTENSION_BY_TYPE.get(fileType)
                : fileName;
        int lastSeparatorIndex = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (lastSeparatorIndex >= 0) {
            value = value.substring(lastSeparatorIndex + 1);
        }

        value = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");

        if (value.isBlank() || extension(value).isBlank()) {
            return DEFAULT_COVER_FILE_NAME + "." + DEFAULT_EXTENSION_BY_TYPE.get(fileType);
        }
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Le departement qui decide ce qu'un joueur voit.
     *
     * <p>Un apprenti porte le departement de sa filiere partout ailleurs -- c'est ce qui
     * le fait compter dans Place, dans les defis et dans le classement de son
     * departement. Ici seulement, son audience est PERF et rien d'autre : le ciblage est
     * exclusif dans les deux sens, un evenement de filiere ne lui parle pas et un
     * evenement PERF ne parle pas a ses camarades.</p>
     *
     * <p>Sans ligne PERF en base, on rend sa filiere : mieux vaut l'ancien comportement
     * qu'une liste vide.</p>
     */
    private UUID audienceDepartmentId(AppUser user) {
        return audienceDepartmentId(user, user.isPerf() ? perfDepartmentId() : null);
    }

    private UUID audienceDepartmentId(AppUser user, UUID perfDepartmentId) {
        return user.isPerf() && perfDepartmentId != null ? perfDepartmentId : user.getDepartment();
    }

    private UUID perfDepartmentId() {
        return departmentRepository.findFirstByVirtualTrue().map(Department::getId).orElse(null);
    }

    private boolean isVisibleToUser(Event event, AppUser user, Instant now) {
        if (event.getPublishedAt() == null || event.getPublishedAt().isAfter(now)) {
            return false;
        }
        if (event.getTargetDepartments().isEmpty()) {
            return true;
        }

        UUID departmentId = TimeTravelContext.departmentIdOr(audienceDepartmentId(user));
        return departmentId != null && event.getTargetDepartments()
                .stream()
                .anyMatch(department -> department.getId().equals(departmentId));
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findWithGraphById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));
    }

    private AppUser currentUser(String keycloakSub) {
        return userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new ForbiddenException("Utilisateur introuvable."));
    }

    private Article findArticle(UUID articleId) {
        if (articleId == null) {
            return null;
        }
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article introuvable."));
    }

    private Set<EventCategory> findCategories(Set<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Set.of();
        }
        List<EventCategory> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new NotFoundException("Catégorie introuvable.");
        }
        return Set.copyOf(categories);
    }

    private Set<Department> findDepartments(Set<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Set.of();
        }
        List<Department> departments = departmentRepository.findAllById(departmentIds);
        if (departments.size() != departmentIds.size()) {
            throw new NotFoundException("Département introuvable.");
        }
        return Set.copyOf(departments);
    }

    /**
     * Ce qu'une page d'evenements coute en decomptes, paye une fois pour la page.
     *
     * <p>Chaque ligne demandait trois requetes : le statut du joueur, le nombre
     * d'inscrits, le nombre en liste d'attente. A quarante evenements cela faisait
     * cent vingt requetes pour une ouverture d'application, par joueur - la meme
     * linearite qu'un commentaire deux methodes plus haut se felicite d'avoir
     * supprimee pour les questionnaires, revenue par une autre porte.</p>
     *
     * <p>Deux requetes groupees suffisent, et {@code statusCountsByEventIds} existait
     * deja : {@code TournamentService} s'en servait, pas celle-ci.</p>
     */
    private record EventPageCounts(
            Map<UUID, EventRegistrationStatus> mine,
            Map<UUID, Long> registered,
            Map<UUID, Long> waitlisted
    ) {
        EventRegistrationStatus statusOf(UUID eventId) {
            return mine.get(eventId);
        }

        long registeredOn(UUID eventId) {
            return registered.getOrDefault(eventId, 0L);
        }

        long waitlistedOn(UUID eventId) {
            return waitlisted.getOrDefault(eventId, 0L);
        }
    }

    private EventPageCounts countsFor(List<Event> events, UUID currentUserId) {
        List<UUID> eventIds = events.stream().map(Event::getId).filter(Objects::nonNull).distinct().toList();
        if (eventIds.isEmpty()) {
            return new EventPageCounts(Map.of(), Map.of(), Map.of());
        }

        Map<UUID, Long> registered = new HashMap<>();
        Map<UUID, Long> waitlisted = new HashMap<>();
        for (Object[] row : registrationRepository.statusCountsByEventIds(eventIds)) {
            UUID eventId = (UUID) row[0];
            long count = ((Number) row[2]).longValue();
            if (row[1] == EventRegistrationStatus.REGISTERED) {
                registered.merge(eventId, count, Long::sum);
            } else if (row[1] == EventRegistrationStatus.WAITLISTED) {
                waitlisted.merge(eventId, count, Long::sum);
            }
        }

        Map<UUID, EventRegistrationStatus> mine = currentUserId == null
                ? Map.of()
                : registrationRepository.findByUserIdAndEventIdIn(currentUserId, eventIds).stream()
                .collect(Collectors.toMap(
                        registration -> registration.getEvent().getId(),
                        EventRegistration::getStatus,
                        (first, second) -> first));

        return new EventPageCounts(mine, registered, waitlisted);
    }

    private EventDto toDto(Event event, UUID currentUserId) {
        Map<UUID, List<EventFriendDto>> friendsByEvent = registeredFriendsByEvent(List.of(event), currentUserId);
        return toDto(event, currentUserId, registeredFriends(friendsByEvent, event),
                countsFor(List.of(event), currentUserId));
    }

    private EventDto toDto(Event event, UUID currentUserId, List<EventFriendDto> registeredFriends,
                           EventPageCounts counts) {
        return toDto(event, currentUserId, registeredFriends,
                event.getId() != null && questionRepository.countByEventId(event.getId()) > 0, counts);
    }

    private EventDto toDto(
            Event event, UUID currentUserId, List<EventFriendDto> registeredFriends, boolean hasQuestions,
            EventPageCounts counts
    ) {
        EventRegistrationStatus currentUserStatus = currentUserId == null ? null : counts.statusOf(event.getId());

        return new EventDto(
                event.getId(),
                event.getTitle(),
                event.getShortDescription(),
                event.getLocation(),
                event.getCoverImageUrl(),
                event.getSource(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getPublishedAt(),
                event.getRegistrationEnabled(),
                event.getCapacity(),
                event.getWaitlistEnabled(),
                toArticleDto(event.getArticle()),
                event.getCategories().stream()
                        .sorted(Comparator.comparing(EventCategory::getName))
                        .map(this::toCategoryDto)
                        .toList(),
                event.getTargetDepartments().stream()
                        .sorted(Comparator.comparing(Department::getShortName))
                        .map(this::toDepartmentDto)
                        .toList(),
                event.getId() == null ? 0 : counts.registeredOn(event.getId()),
                event.getId() == null ? 0 : counts.waitlistedOn(event.getId()),
                currentUserStatus,
                registeredFriends,
                hasQuestions,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private Set<UUID> eventIdsWithQuestions(List<Event> events) {
        List<UUID> eventIds = events.stream().map(Event::getId).filter(Objects::nonNull).toList();
        if (eventIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(questionRepository.findEventIdsWithQuestions(eventIds));
    }

    private Map<UUID, List<EventFriendDto>> registeredFriendsByEvent(List<Event> events, UUID currentUserId) {
        if (currentUserId == null) {
            return Map.of();
        }

        List<UUID> eventIds = events.stream()
                .map(Event::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> friendIds = friendshipRepository.findAcceptedFriendIds(currentUserId);
        if (friendIds.isEmpty()) {
            return Map.of();
        }

        return registrationRepository.findByEventIdInAndUserIdIn(eventIds, friendIds)
                .stream()
                .collect(Collectors.groupingBy(
                        registration -> registration.getEvent().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                registrations -> registrations.stream()
                                        .sorted(FRIEND_ORDER)
                                        .map(EventService::toFriendDto)
                                        .toList()
                        )
                ));
    }

    private static List<EventFriendDto> registeredFriends(Map<UUID, List<EventFriendDto>> friendsByEvent, Event event) {
        if (event.getId() == null) {
            return List.of();
        }
        return friendsByEvent.getOrDefault(event.getId(), List.of());
    }

    private static EventFriendDto toFriendDto(EventRegistration registration) {
        AppUser friend = registration.getUser();
        return new EventFriendDto(
                friend.getId(),
                friend.getDisplayName(),
                friend.getDepartment(),
                registration.getStatus()
        );
    }

    private EventRegistrantDto toRegistrantDto(EventRegistration registration) {
        AppUser user = registration.getUser();
        return new EventRegistrantDto(
                registration.getId(),
                registration.getStatus(),
                registration.getCreatedAt(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getDepartment(),
                user.isPerf()
        );
    }

    private EventArticleDto toArticleDto(Article article) {
        return article == null ? null : new EventArticleDto(article.getId(), article.getTitle());
    }

    private EventCategoryDto toCategoryDto(EventCategory category) {
        return new EventCategoryDto(category.getId(), category.getName(), category.getSlug());
    }

    private EventDepartmentDto toDepartmentDto(Department department) {
        return new EventDepartmentDto(department.getId(), department.getName(), department.getShortName());
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (value.isBlank()) {
            throw new BadRequestException("Le nom de la catégorie est requis.");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String slug(String value) {
        String slug = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return slug.isBlank() ? "categorie" : slug;
    }
}
