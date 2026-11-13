package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.TooManyRequestsException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentDto;
import fr.insastronaute.webapi.place.dto.PlacePixelAuthorDto;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.dto.PlaceBonusStatusDto;
import fr.insastronaute.webapi.place.dto.PlacePixelRequest;
import fr.insastronaute.webapi.place.dto.PlacePixelUpdate;
import fr.insastronaute.webapi.place.dto.PlaceCanvasEvent;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;

@Service
public class PlaceService {

    private static final String CANVAS_TOPIC = "/topic/canvas-updates";

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    private fr.insastronaute.webapi.department.repository.DepartmentRepository departmentRepository;

    /** Par setter : les tests qui ne regardent pas les departements n'ont pas a le connaitre. */
    @org.springframework.beans.factory.annotation.Autowired
    void setDepartmentRepository(fr.insastronaute.webapi.department.repository.DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    private final UserRepository userRepository;
    private final PlaceConfigService configService;
    private final PlaceCooldownStore cooldownStore;
    private final PlaceCanvasStore canvasStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final PlacePixelPlacementRepository placements;

    public PlaceService(
            UserRepository userRepository,
            PlaceConfigService configService,
            PlaceCooldownStore cooldownStore,
            PlaceCanvasStore canvasStore,
            SimpMessagingTemplate messagingTemplate,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            PlacePixelPlacementRepository placements
    ) {
        this.userRepository = userRepository;
        this.configService = configService;
        this.cooldownStore = cooldownStore;
        this.canvasStore = canvasStore;
        this.messagingTemplate = messagingTemplate;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.placements = placements;
    }

    /** Le departement d'un joueur et sa couleur, ou rien s'il n'en a pas. */
    @Transactional(readOnly = true)
    public Optional<PlaceDepartmentDto> departmentOf(String keycloakSubject) {
        return userRepository.findByKeycloakSub(keycloakSubject).flatMap(this::departmentOf);
    }

    private Optional<PlaceDepartmentDto> departmentOf(AppUser user) {
        if (user.getDepartment() == null || departmentRepository == null) {
            return Optional.empty();
        }
        return departmentRepository.findById(user.getDepartment())
                .map(department -> new PlaceDepartmentDto(department.getShortName(), department.getColor()));
    }

    /**
     * Le departement enregistre du joueur, celui que lit la fresque des departements.
     *
     * <p>Pas celui d'un voyage dans le temps : la fresque est calculee depuis la base,
     * et un pixel annonce en direct aux couleurs d'un departement emprunte changerait
     * de couleur au rechargement suivant.</p>
     */
    private Optional<Department> persistedDepartmentOf(AppUser user) {
        if (user.getPersistedDepartment() == null || departmentRepository == null) {
            return Optional.empty();
        }
        return departmentRepository.findById(user.getPersistedDepartment());
    }

    private String departmentColorOf(AppUser user) {
        return persistedDepartmentOf(user).map(Department::getColor).orElse(null);
    }

    /**
     * La fresque des departements est-elle a montrer ?
     *
     * <p>Les resultats la rouvrent en lecture : le classement des territoires se lit
     * dessus, et sans son onglet il renverrait a une fresque devenue invisible. Une
     * fresque jamais ouverte, elle, ne reapparait pas pour autant.</p>
     */
    public boolean isDepartmentCanvasVisible() {
        PlaceConfig config = configService.currentConfig();
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        return config.isDepartmentCanvasEnabled() && (config.isVisibleAt(now) || config.showsResultsAt(now));
    }

    @Transactional
    public PlacePixelUpdate placePixel(String keycloakSubject, PlacePixelRequest request) {
        PlaceConfig config = configService.currentConfig();
        validateEvent(config);
        validateCoordinates(request, config);
        canvasStore.initialize(config.getCanvasWidth(), config.getCanvasHeight());

        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSubject)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (Boolean.TRUE.equals(user.getPlaceBanned())) {
            throw new ForbiddenException("Le canevas t'est fermé à la suite d'une modération.");
        }

        if (request.wantsBonus()) {
            return placePurchasedPixel(user, config, request);
        }

        PlaceCooldownStore.CooldownType cooldownType = nextCooldownType(user, config);
        long cooldownMs = cooldownType == PlaceCooldownStore.CooldownType.CHALLENGE
                ? config.getChallengeCooldownMs()
                : config.getBaseCooldownMs();
        PlaceCooldownStore.CooldownAttempt cooldown = cooldownStore.tryAcquire(cooldownType, user.getId(), cooldownMs);
        if (!cooldown.acquired()) {
            throw new TooManyRequestsException(cooldown.remainingCooldownMs());
        }

        PlaceCanvasDescriptor descriptor;
        try {
            descriptor = canvasStore.placePixel(request.x(), request.y(), request.color());
        } catch (PlaceResizeLockedException locked) {
            cooldownStore.release(cooldownType, user.getId(), cooldown.owner());
            throw locked;
        }
        // Un pixel attendu rouvre le droit d'en racheter un.
        user.setPlaceLastPixelPurchased(false);
        if (cooldownType == PlaceCooldownStore.CooldownType.CHALLENGE) {
            user.setPlaceBonusPixelsUsed(user.getPlaceBonusPixelsUsed() + 1);
            // Only the bonus counter is tracked per player, so only a bonus
            // pixel is worth re-evaluating.
            autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.PLACE_PIXEL_PLACED);
        }
        recordPlacement(descriptor.generation(), request, user);
        Instant deadline = cooldown.deadline() == null ? fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().plusMillis(cooldownMs) : cooldown.deadline();
        PlacePixelUpdate update = new PlacePixelUpdate(request.x(), request.y(), request.color(),
                Long.toString(descriptor.revision()), cooldownType, cooldownMs, deadline);
        messagingTemplate.convertAndSend(CANVAS_TOPIC, PlaceCanvasEvent.pixel(
                request.x(), request.y(), request.color(), descriptor.revision(), departmentColorOf(user)));
        return update;
    }

    @Transactional
    /**
     * Pose un pixel payé, sans toucher au cooldown — c'est précisément ce que le
     * joueur achète.
     *
     * Deux achats d'affilée sont refusés : sans un pixel attendu entre les deux,
     * le cooldown ne serait plus une attente mais un tarif, et un gros solde
     * effacerait la contrainte pour toute la durée de l'événement. Le prix monte
     * d'un cran à chaque achat pour la même raison, à l'échelle de l'événement.
     */
    private PlacePixelUpdate placePurchasedPixel(AppUser user, PlaceConfig config, PlacePixelRequest request) {
        if (user.isPlaceLastPixelPurchased()) {
            throw new CodedBadRequestException(
                    "PLACE_BONUS_CHAINED",
                    "Pose un pixel en cooldown normal avant de racheter un pixel bonus."
            );
        }
        long price = bonusPrice(user, config);
        // En PE et non plus en tokens : Place est un evenement, et les tokens gagnes
        // ailleurs n'ont pas a acheter de l'avance sur la fresque.
        if (user.getEventPoints() < price) {
            throw new CodedBadRequestException(
                    "PLACE_BONUS_INSUFFICIENT_EVENT_POINTS",
                    "Il te faut " + price + " PE pour un pixel bonus."
            );
        }

        // Le débit précède l'écriture : le canevas vit dans Redis, hors de la
        // transaction. Si le placement échoue, le rollback annule le débit ;
        // l'inverse aurait offert le pixel.
        user.setEventPoints(user.getEventPoints() - price);
        currencyLedger.record(user, fr.insastronaute.webapi.economy.domain.CurrencySource.PLACE_BONUS_PIXEL, 0, -price, null);
        user.setPlacePurchasedBonusPixels(user.getPlacePurchasedBonusPixels() + 1);
        user.setPlaceLastPixelPurchased(true);

        PlaceCanvasDescriptor descriptor = canvasStore.placePixel(request.x(), request.y(), request.color());
        recordPlacement(descriptor.generation(), request, user);

        // Le cooldown en cours n'est ni pris ni avancé : le décompte affiché reste
        // celui du prochain pixel gratuit — et ce pixel se pose sur le cooldown des
        // défis tant qu'il en reste. Lire toujours le cooldown de base annonçait
        // « disponible » à un joueur encore en attente, que la pose refusait ensuite.
        PlaceCooldownStore.CooldownType nextType = nextCooldownType(user, config);
        long remaining = cooldownStore.remainingMs(nextType, user.getId());
        Instant nextPlacementAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()
                .plusMillis(remaining);
        messagingTemplate.convertAndSend(CANVAS_TOPIC, PlaceCanvasEvent.pixel(
                request.x(), request.y(), request.color(), descriptor.revision(), departmentColorOf(user)));
        return new PlacePixelUpdate(request.x(), request.y(), request.color(),
                Long.toString(descriptor.revision()), nextType, remaining,
                nextPlacementAt, true, user.getEventPoints(), bonusPrice(user, config));
    }

    @Transactional(readOnly = true)
    /**
     * Garde la trace de qui a posé quoi, et quand.
     *
     * <p>Sans elle, une image choquante ne peut être que recouverte : rien ne dit
     * qui l'a dessinée, et la personne recommence trois cases plus loin. C'est aussi
     * elle que relit la fresque des départements.</p>
     */
    private void recordPlacement(String generation, PlacePixelRequest request, AppUser user) {
        placements.save(new PlacePixelPlacement(
                generation,
                request.x(),
                request.y(),
                request.color().toLowerCase(),
                user.getId(),
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()
        ));
    }

    /**
     * L'auteur d'une case, pour le survol : son nom, son département, et quand.
     *
     * <p>Vide quand l'organisation a choisi de ne pas montrer les auteurs, ou
     * quand le pixel date d'avant l'enregistrement : mieux vaut ne rien afficher
     * qu'attribuer la case au premier qui repassera dessus.</p>
     */
    @Transactional(readOnly = true)
    public Optional<PlacePixelAuthorDto> pixelAuthor(int x, int y) {
        if (!configService.currentConfig().isShowPixelAuthors()) {
            return Optional.empty();
        }
        String generation = canvasStore.descriptor().generation();
        return placements
                .findByGenerationAndXAndYOrderByPlacedAtDesc(generation, x, y, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(placement -> {
                    Optional<AppUser> author = userRepository.findById(placement.getUserId());
                    return PlacePixelAuthorDto.forPlayers(
                            placement.getX(),
                            placement.getY(),
                            placement.getColor(),
                            author.map(AppUser::getDisplayName).orElse("Compte supprimé"),
                            placement.getPlacedAt(),
                            author.flatMap(this::persistedDepartmentOf).map(Department::getShortName).orElse(null)
                    );
                });
    }

    public PlaceBonusStatusDto bonusStatus(String keycloakSubject) {
        PlaceConfig config = configService.currentConfig();
        AppUser user = userRepository.findByKeycloakSub(keycloakSubject)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        long price = bonusPrice(user, config);
        boolean affordable = user.getEventPoints() >= price;
        boolean blocked = user.isPlaceLastPixelPurchased();
        return new PlaceBonusStatusDto(price, user.getEventPoints(), affordable, blocked, affordable && !blocked);
    }

    private static long bonusPrice(AppUser user, PlaceConfig config) {
        return config.getBonusPixelPrice()
                + config.getBonusPixelPriceIncrement() * Math.max(0, user.getPlacePurchasedBonusPixels());
    }

    public PlaceCanvasSnapshot canvasPng() {
        PlaceConfig config = configService.currentConfig();
        canvasStore.initialize(config.getCanvasWidth(), config.getCanvasHeight());
        return canvasStore.snapshot();
    }

    /**
     * Le cooldown sur lequel se posera le prochain pixel normal.
     *
     * <p>Une seule regle, lue par la pose et par l'achat : si chacun la recopiait,
     * l'achat pourrait annoncer un decompte que la pose ne respecte pas.</p>
     */
    private static PlaceCooldownStore.CooldownType nextCooldownType(AppUser user, PlaceConfig config) {
        return availableBonusPixels(user, config) > 0
                ? PlaceCooldownStore.CooldownType.CHALLENGE
                : PlaceCooldownStore.CooldownType.BASE;
    }

    private static long availableBonusPixels(AppUser user, PlaceConfig config) {
        long earnedPixels = Math.max(0, user.getChallengePoints()) / config.getChallengeRatio();
        return Math.max(0, earnedPixels - Math.max(0, user.getPlaceBonusPixelsUsed()));
    }

    /**
     * L'evenement est-il ouvert, tel que le front doit le montrer ou non.
     *
     * <p>Meme regle que {@link #validateEvent}, a dessein : une tuile visible sur un
     * evenement qui refuse les pixels est pire que pas de tuile du tout.</p>
     */
    public boolean isEventVisible() {
        return configService.currentConfig()
                .isVisibleAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    /** Les resultats remplacent-ils la fresque ? Voir {@link PlaceConfig#showsResultsAt}. */
    public boolean showsResults() {
        return configService.currentConfig()
                .showsResultsAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    public boolean isEventOpen() {
        return configService.currentConfig()
                .isOpenAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    private static void validateEvent(PlaceConfig config) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (!config.isOpenAt(now)) {
            throw new CodedBadRequestException("PLACE_INACTIVE", "L'événement r/place n'est pas actif.");
        }
    }

    private static void validateCoordinates(PlacePixelRequest request, PlaceConfig config) {
        if (request.x() < 0 || request.x() >= config.getCanvasWidth()
                || request.y() < 0 || request.y() >= config.getCanvasHeight()) {
            throw new CodedBadRequestException("PLACE_INVALID_COORDINATES", "Les coordonnées sont hors des limites du canevas.");
        }
    }
}
