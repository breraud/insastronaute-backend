package fr.insastronaute.webapi.sts.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandRequest;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.dto.StsCombatStateResponse;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.dto.StsClaimRewardRequest;
import fr.insastronaute.webapi.sts.dto.StsClaimRewardResponse;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionRequest;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionResponse;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeRequest;
import fr.insastronaute.webapi.sts.dto.StsRestActionRequest;
import fr.insastronaute.webapi.sts.dto.StsRestActionResponse;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsCurrentRunResponse;
import fr.insastronaute.webapi.sts.dto.StsDebugCombatRequest;
import fr.insastronaute.webapi.sts.dto.StsDebugCombatResponse;
import fr.insastronaute.webapi.sts.dto.StsEnterNodeRequest;
import fr.insastronaute.webapi.sts.dto.StsEnterNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsRunStateDto;
import fr.insastronaute.webapi.sts.dto.StsRetireRunResponse;
import fr.insastronaute.webapi.sts.dto.StsRetreatPreviewResponse;
import fr.insastronaute.webapi.sts.dto.StsStartRunRequest;
import fr.insastronaute.webapi.sts.multiplayer.service.StsPvpService;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentRoller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class StsRunService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    private static final String RESULT_VICTORY = "Victory";
    private static final Set<String> ENCOUNTER_NODE_TYPES = Set.of("Combat", "Elite", "Boss");
    /** Le nœud dont la table sert de repli quand aucune n'est écrite pour celui qu'on complète. */
    private static final String DEFAULT_REWARD_NODE_TYPE = "Combat";
    private static final String REST_NODE_TYPE = "Rest";
    /** Le défaut du client, RunManager.maxRestCharges. */
    private static final int DEFAULT_MAX_REST_CHARGES = 3;
    private static final String ITI_RELIC_ID = "ITIRelic";

    private final StsRunRepository stsRunRepository;
    private final UserRepository userRepository;
    private final StsGameDataService gameDataService;
    private final StsAuthoritativeCombatService stsAuthoritativeCombatService;
    private final StsPvpService stsPvpService;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.Timer enterNodeTimer;
    private final io.micrometer.core.instrument.Timer combatCreationTimer;
    private final io.micrometer.core.instrument.Timer coldCombatCreationTimer;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final SecureRandom random = new SecureRandom();
    private final CardCatalogFactory cardCatalogFactory = new CardCatalogFactory();

    public StsRunService(
            StsRunRepository stsRunRepository,
            UserRepository userRepository,
            StsGameDataService gameDataService,
            StsAuthoritativeCombatService stsAuthoritativeCombatService,
            StsPvpService stsPvpService,
            ObjectMapper objectMapper,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this(stsRunRepository, userRepository, gameDataService, stsAuthoritativeCombatService,
                stsPvpService, objectMapper, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                autoChallengeTriggerPublisher);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StsRunService(
            StsRunRepository stsRunRepository,
            UserRepository userRepository,
            StsGameDataService gameDataService,
            StsAuthoritativeCombatService stsAuthoritativeCombatService,
            StsPvpService stsPvpService,
            ObjectMapper objectMapper,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this.stsRunRepository = stsRunRepository;
        this.userRepository = userRepository;
        this.gameDataService = gameDataService;
        this.stsAuthoritativeCombatService = stsAuthoritativeCombatService;
        this.stsPvpService = stsPvpService;
        this.objectMapper = objectMapper;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.enterNodeTimer = io.micrometer.core.instrument.Timer
                .builder("sts.run.enter_node")
                .description("Time to enter a map node, including building the combat it starts")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);
        // Building a combat reads the card and enemy catalogs. The first combat of a data version
        // pays for loading them and every later one does not, so the two are measured apart:
        // averaging them together hides both the cost of the first and the speed of the rest.
        this.combatCreationTimer = io.micrometer.core.instrument.Timer
                .builder("sts.combat.create")
                .description("Time to build an authoritative combat from the run and the catalogs")
                .tag("catalog", "warm")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);
        this.coldCombatCreationTimer = io.micrometer.core.instrument.Timer
                .builder("sts.combat.create")
                .description("Time to build an authoritative combat from the run and the catalogs")
                .tag("catalog", "cold")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);
    }

    @Transactional
    public StsRunStateDto startRun(AppUser owner, StsStartRunRequest request) {
        Optional<StsRun> activeRun = stsRunRepository
                .findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(owner.getKeycloakSub(), StsRunStatus.Active);
        if (activeRun.isPresent()) {
            try {
                ensureRunStateDefaults(activeRun.get(), request.character());
                return toDto(activeRun.get());
            } catch (Exception exception) {
                StsRun run = activeRun.get();
                run.setStatus(StsRunStatus.Abandoned);
                run.setEnteredNodeId(null);
                run.setActiveEncounter(null);
                run.setActiveCombat(null);
                run.setActiveEvent(null);
                stsRunRepository.save(run);
            }
        }

        AppUser lockedOwner = userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        activeRun = stsRunRepository
                .findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(owner.getKeycloakSub(), StsRunStatus.Active);
        if (activeRun.isPresent()) {
            try {
                ensureRunStateDefaults(activeRun.get(), request.character());
                return toDto(activeRun.get());
            } catch (Exception exception) {
                StsRun run = activeRun.get();
                run.setStatus(StsRunStatus.Abandoned);
                run.setEnteredNodeId(null);
                run.setActiveEncounter(null);
                run.setActiveCombat(null);
                run.setActiveEvent(null);
                stsRunRepository.save(run);
            }
        }

        String dataVersion = gameDataService.latestVersion();
        JsonNode character = gameDataService.character(dataVersion, request.character());
        String characterId = character.path("characterId").asText();
        int hp = character.path("hp").asInt();

        StsRun run = new StsRun();
        run.setOwner(lockedOwner);
        run.setStatus(StsRunStatus.Active);
        run.setDataVersion(dataVersion);
        run.setSelectedCharacter(characterId);
        run.setAct(startingAct(dataVersion));
        run.setCurrentFloor(0);
        run.setCurrentNodeId(0);
        run.setPlayerState(playerState(hp));
        run.setRunInventory(startingInventory(dataVersion, characterId));
        run.setMapState(generateMap(gameDataService.mapGeneration(dataVersion)));
        run.setPendingRewards(objectMapper.createArrayNode());

        return toDto(stsRunRepository.save(run));
    }

    private void ensureRunStateDefaults(StsRun run, String requestedCharacterId) {
        boolean changed = false;

        if (run.getStatus() == null) {
            run.setStatus(StsRunStatus.Active);
            changed = true;
        }

        if (run.getDataVersion() == null || run.getDataVersion().isBlank()) {
            run.setDataVersion(gameDataService.latestVersion());
            changed = true;
        }

        if (run.getSelectedCharacter() == null || run.getSelectedCharacter().isBlank()) {
            String fallbackCharacter = requestedCharacterId;
            if (fallbackCharacter == null || fallbackCharacter.isBlank()) {
                fallbackCharacter = "EP";
            }
            String normalizedCharacter = gameDataService.character(run.getDataVersion(), fallbackCharacter).path("characterId").asText(fallbackCharacter);
            run.setSelectedCharacter(normalizedCharacter);
            changed = true;
        }

        if (run.getAct() == null) {
            run.setAct(startingAct(run.getDataVersion()));
            changed = true;
        }

        if (run.getPendingRewards() == null || !run.getPendingRewards().isArray()) {
            run.setPendingRewards(objectMapper.createArrayNode());
            changed = true;
        }

        if (run.getMapState() == null || run.getMapState().isNull()) {
            run.setMapState(generateMap(gameDataService.mapGeneration(run.getDataVersion())));
            run.setCurrentNodeId(0);
            run.setCurrentFloor(0);
            changed = true;
        }

        if (run.getRunInventory() == null || run.getRunInventory().isNull()) {
            run.setRunInventory(startingInventory(run.getDataVersion(), run.getSelectedCharacter()));
            changed = true;
        }

        if (run.getPlayerState() == null || run.getPlayerState().isNull()) {
            JsonNode character = gameDataService.character(run.getDataVersion(), run.getSelectedCharacter());
            int hp = character.path("hp").asInt(1);
            run.setPlayerState(playerState(Math.max(1, hp)));
            changed = true;
        }

        if (run.getCurrentNodeId() == null) {
            run.setCurrentNodeId(0);
            changed = true;
        }

        if (run.getCurrentFloor() == null) {
            run.setCurrentFloor(0);
            changed = true;
        }

        if (run.getCurrentNodeId() != null && run.getMapState() != null && !run.getMapState().isNull()) {
            boolean nodeExists = false;
            for (JsonNode node : run.getMapState().path("nodes")) {
                if (node.path("id").asInt(Integer.MIN_VALUE) == run.getCurrentNodeId()) {
                    nodeExists = true;
                    break;
                }
            }
            if (!nodeExists) {
                run.setCurrentNodeId(0);
                run.setCurrentFloor(0);
                changed = true;
            }
        }

        if (changed) {
            stsRunRepository.save(run);
        }
    }

    @Transactional(readOnly = true)
    public StsCurrentRunResponse currentRun(AppUser owner) {
        // L'état du compte accompagne la réponse même sans run : le menu principal le demande
        // précisément quand aucune n'est en cours.
        boolean secretCardsUnlocked = owner.hasUnlockedStsSecretCards();
        return stsRunRepository.findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(owner.getKeycloakSub(), StsRunStatus.Active)
                .map(run -> new StsCurrentRunResponse(true, toDto(run), secretCardsUnlocked))
                .orElseGet(() -> new StsCurrentRunResponse(false, null, secretCardsUnlocked));
    }

    @Transactional
    public JsonNode authoritativeCombatSnapshot(String ownerSub, UUID runId) {
        StsRun run = stsRunRepository.findOwnedWithLock(runId, ownerSub)
                .orElseThrow(() -> new NotFoundException("Run STS introuvable."));
        requireActive(run);
        if (run.getActiveCombat() == null || run.getActiveCombat().isNull()) {
            if (run.getActiveEncounter() == null || run.getActiveEncounter().isNull()) {
                throw new ConflictException("Aucun combat STS authoritative actif.");
            }
            run.setActiveCombat(createAuthoritativeCombat(run));
        }
        return run.getActiveCombat();
    }

    @Transactional
    public StsEnterNodeResponse enterNode(AppUser owner, UUID runId, int nodeId, StsEnterNodeRequest request) {
        return enterNodeTimer.record(() -> enterNodeInternal(owner, runId, nodeId, request));
    }

    private StsEnterNodeResponse enterNodeInternal(
            AppUser owner,
            UUID runId,
            int nodeId,
            StsEnterNodeRequest request
    ) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if (request != null && request.clientKnownCurrentNodeId() != null && !request.clientKnownCurrentNodeId().equals(run.getCurrentNodeId())) {
            throw new ConflictException("La position courante connue du client est obsolète.");
        }
        if (run.getEnteredNodeId() != null && run.getEnteredNodeId() != nodeId) {
            throw new ConflictException("Un autre node STS est déjà en cours.");
        }

        ObjectNode node = node(run.getMapState(), nodeId);
        if (node.path("completed").asBoolean(false)) {
            throw new ConflictException("Node STS déjà complété.");
        }
        if (run.getEnteredNodeId() != null && run.getEnteredNodeId() == nodeId) {
            if (ENCOUNTER_NODE_TYPES.contains(node.path("type").asText())
                    && run.getActiveEncounter() != null
                    && !run.getActiveEncounter().isNull()
                    && (run.getActiveCombat() == null || run.getActiveCombat().isNull())) {
                run.setActiveCombat(createAuthoritativeCombat(run));
            }
            return enterNodeResponse(run, nodeId, node.path("type").asText());
        }
        requireReachable(run, nodeId);

        node.put("visited", true);
        run.setEnteredNodeId(nodeId);
        String nodeType = node.path("type").asText();
        if (ENCOUNTER_NODE_TYPES.contains(nodeType)) {
            run.setActiveEncounter(createEncounter(run, nodeId, nodeType));
            run.setActiveCombat(createAuthoritativeCombat(run));
            run.setActiveEvent(null);
        } else if ("Event".equals(nodeType)) {
            run.setActiveEvent(existingOrChooseEvent(run, nodeId));
            run.setActiveEncounter(null);
            run.setActiveCombat(null);
        } else {
            run.setActiveEncounter(null);
            run.setActiveCombat(null);
            run.setActiveEvent(null);
            if (REST_NODE_TYPE.equals(nodeType)) {
                openRestSite(run);
            }
        }

        return enterNodeResponse(run, nodeId, nodeType);
    }

    private StsEnterNodeResponse enterNodeResponse(StsRun run, int nodeId, String nodeType) {
        JsonNode activeEncounter = run.getActiveEncounter();
        JsonNode activeEvent = run.getActiveEvent();
        return new StsEnterNodeResponse(
                true,
                run.getId(),
                nodeId,
                nodeType,
                apiJson(activeEncounter),
                apiJson(run.getActiveCombat()),
                eventId(activeEvent),
                apiJson(activeEvent),
                apiJson(run.getPlayerState())
        );
    }

    @Transactional
    public StsChooseEventOptionResponse chooseEventOption(
            AppUser owner,
            UUID runId,
            String eventInstanceId,
            StsChooseEventOptionRequest request
    ) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if (request == null || request.optionId() == null || request.optionId().isBlank()) {
            throw new BadRequestException("Option d'event STS requise.");
        }
        JsonNode activeEvent = run.getActiveEvent();
        if (activeEvent == null || activeEvent.isNull()) {
            throw new ConflictException("Aucun event STS actif.");
        }
        if (!activeEvent.path("eventInstanceId").asText().equals(eventInstanceId)) {
            throw new ConflictException("Event STS obsolète.");
        }
        int nodeId = activeEvent.path("nodeId").asInt(run.getEnteredNodeId() == null ? -1 : run.getEnteredNodeId());
        if (run.getEnteredNodeId() == null || run.getEnteredNodeId() != nodeId) {
            throw new ConflictException("Cet event STS n'est pas en cours.");
        }
        ObjectNode node = node(run.getMapState(), nodeId);
        if (!"Event".equals(node.path("type").asText())) {
            throw new ConflictException("Le node STS courant n'est pas un event.");
        }

        JsonNode option = eventOption(activeEvent, request.optionId());
        ObjectNode runInventoryPatch = eventRunInventoryPatch();
        ObjectNode accountInventoryPatch = objectMapper.createObjectNode();
        accountInventoryPatch.put("tokenDelta", 0);
        ArrayNode createdRewards = objectMapper.createArrayNode();
        boolean eventContinues = applyEventEntries(
                run, option.path("entries"), request, runInventoryPatch, createdRewards, request.optionId());
        replacePendingRewards(run, createdRewards);

        if (eventContinues) {
            // L'option en a ouvert d'autres : le nœud reste en cours et le client
            // réaffiche les options que le serveur vient de poser. Son message de fin
            // part quand même : « Vous approchez de la fin ! » est écrit pour être lu
            // entre deux paliers, pas à la sortie du nœud.
            return new StsChooseEventOptionResponse(
                    true,
                    eventInstanceId,
                    false,
                    apiJson(run.getPlayerState()),
                    apiJson(runInventoryPatch),
                    apiJson(accountInventoryPatch),
                    apiJson(unclaimedRewards(run)),
                    apiJson(mapPatch(run)),
                    option.path("completionMessage").asText(null),
                    apiJson(run.getActiveEvent())
            );
        }

        node.put("completed", true);
        run.setCurrentNodeId(nodeId);
        run.setCurrentFloor(node.path("floor").asInt());
        run.setEnteredNodeId(null);
        run.setActiveEvent(null);
        run.setActiveEncounter(null);
        run.setActiveCombat(null);

        return new StsChooseEventOptionResponse(
                true,
                eventInstanceId,
                true,
                apiJson(run.getPlayerState()),
                apiJson(runInventoryPatch),
                apiJson(accountInventoryPatch),
                apiJson(unclaimedRewards(run)),
                apiJson(mapPatch(run)),
                option.path("completionMessage").asText(null),
                null
        );
    }

    @Transactional
    public StsCompleteNodeResponse completeNode(AppUser owner, UUID runId, int nodeId, StsCompleteNodeRequest request) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if ((request == null || request.encounterInstanceId() == null || request.result() == null)
                && run.getActiveCombat() != null) {
            StsAuthoritativeCombatService.AuthoritativeCompletionSnapshot snapshot =
                    stsAuthoritativeCombatService.snapshotForCompletion(run);
            if (snapshot != null) {
                return completeAuthoritativeCombatNode(run, nodeId, snapshot);
            }
        }
        ObjectNode node = node(run.getMapState(), nodeId);
        if (run.getEnteredNodeId() == null) {
            if (node.path("completed").asBoolean(false)
                    && run.getCurrentNodeId() != null
                    && run.getCurrentNodeId() == nodeId) {
                return completionResponse(
                        run,
                        objectMapper.createObjectNode(),
                        objectMapper.createObjectNode()
                );
            }
            throw new ConflictException("Ce node STS n'est pas en cours.");
        }
        if (run.getEnteredNodeId() != nodeId) {
            throw new ConflictException("Ce node STS n'est pas en cours.");
        }

        if (node.path("completed").asBoolean(false)) {
            throw new ConflictException("Node STS déjà complété.");
        }

        String nodeType = node.path("type").asText();
        validateCompletion(run, nodeType, request);
        // Un feu de camp ne rend plus ses points de vie au client : le soin s'y décide
        // désormais dans rest(), et accepter playerHpAfter ici rouvrirait précisément
        // le trou qu'on vient de fermer — il suffisait de déclarer ses PV pleins.
        int playerHpAfter = request == null
                || request.playerHpAfter() == null
                || REST_NODE_TYPE.equals(nodeType)
                ? run.getPlayerState().path("currentHp").asInt()
                : request.playerHpAfter();
        ((ObjectNode) run.getPlayerState()).put("currentHp", playerHpAfter);

        if (ENCOUNTER_NODE_TYPES.contains(nodeType) && request != null && !isVictory(request.result())) {
            run.setStatus(StsRunStatus.Failed);
            autoChallengeTriggerPublisher.publish(run.getOwner().getId(), AutoChallengeTrigger.STS_RUN_ENDED);
            run.setEnteredNodeId(null);
            run.setActiveEncounter(null);
            run.setActiveCombat(null);
            run.setActiveEvent(null);
            stsPvpService.grantRunEndUnlocks(run);
            // Une défaite ne rapporte qu'une part de ce qu'un retrait aurait rendu, réglée par
            // tokens.gameOverMultiplier. À 0 — le défaut — elle ne rapporte toujours rien, et
            // seul le retrait volontaire après un boss complété paie.
            ObjectNode gameOverAccountPatch = objectMapper.createObjectNode();
            gameOverAccountPatch.put("tokenDelta", grantGameOverTokens(run));
            return completionResponse(run, objectMapper.createObjectNode(), gameOverAccountPatch);
        }

        ArrayNode createdRewards = generateRewards(run, nodeType);
        replacePendingRewards(run, createdRewards);

        node.put("completed", true);
        run.setCurrentNodeId(nodeId);
        run.setCurrentFloor(node.path("floor").asInt());
        run.setEnteredNodeId(null);
        run.setActiveEncounter(null);
        run.setActiveCombat(null);
        run.setActiveEvent(null);

        // Les jetons ne sont attribués qu'une fois, à la vraie fin de la run (retrait ou
        // défaite) : les accorder ici aussi les faisait doubler, indifférents à l'acte atteint.
        ObjectNode runInventoryPatch = objectMapper.createObjectNode();
        ObjectNode accountInventoryPatch = objectMapper.createObjectNode();
        accountInventoryPatch.put("tokenDelta", 0);
        return completionResponse(run, runInventoryPatch, accountInventoryPatch);
    }

    @Transactional
    public StsClaimRewardResponse claimReward(AppUser owner, UUID runId, String rewardId, StsClaimRewardRequest request) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        ArrayNode rewards = (ArrayNode) run.getPendingRewards();
        ObjectNode reward = reward(rewards, rewardId);
        if (reward.path("claimed").asBoolean(false)) {
            throw new ConflictException("Reward STS déjà claim.");
        }

        ObjectNode inventory = (ObjectNode) run.getRunInventory();
        switch (reward.path("type").asText()) {
            case "Gold" -> inventory.put("gold", inventory.path("gold").asInt() + reward.path("amount").asInt());
            case "CardChoice" -> claimCardChoice(run, inventory, reward, request);
            case "Relic" -> claimRelic(run, inventory, reward);
            case "BaseRelicUpgrade" -> claimBaseRelicUpgrade(inventory, reward);
            default -> throw new BadRequestException("Type de reward STS inconnu.");
        }
        reward.put("claimed", true);

        run.setRunInventory(inventory.deepCopy());
        run.setPendingRewards(rewards.deepCopy());
        return new StsClaimRewardResponse(
                true,
            apiJson(run.getPlayerState()),
                apiJson(run.getRunInventory()),
                apiJson(unclaimedRewards(run))
        );
    }

    @Transactional
    public void resetRun(AppUser owner, UUID runId) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        run.setStatus(StsRunStatus.Abandoned);
        run.setEnteredNodeId(null);
        run.setActiveEncounter(null);
        run.setActiveCombat(null);
        run.setActiveEvent(null);
        stsPvpService.grantRunEndUnlocks(run);
    }

    @Transactional
    public StsCombatStateResponse combatState(AppUser owner, UUID runId) {
        StsRun run = ownedRunWithLock(owner, runId);
        return stsAuthoritativeCombatService.combatState(run);
    }

    @Transactional
    public StsCombatCommandResponse submitCombatCommand(AppUser owner, UUID runId, StsCombatCommandRequest request) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        return stsAuthoritativeCombatService.submitCommand(run, request);
    }

    @Transactional
    public StsRetireRunResponse retireRun(AppUser owner, UUID runId) {
        StsRun run = ownedRunWithLock(owner, runId);
        if (run.getStatus() == StsRunStatus.Retired) {
            JsonNode rules = gameDataService.runScoreRewards(run.getDataVersion());
            AppUser lockedUser = userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())
                    .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
            // Already retired, so unlocks were already claimed (or never earned); nothing new to report.
            return retirementResponse(run, 0L, lockedUser.getTokens(), rules.path("score"), rules.path("tokens"), List.of());
        }
        requireActive(run);
        if (run.getEnteredNodeId() != null || run.getActiveEncounter() != null || run.getActiveEvent() != null) {
            throw new ConflictException("La run STS ne peut pas être arrêtée pendant un node.");
        }

        ObjectNode currentNode = node(run.getMapState(), run.getCurrentNodeId());
        if (!"Boss".equals(currentNode.path("type").asText())
                || !currentNode.path("completed").asBoolean(false)) {
            throw new ConflictException("La run STS ne peut être arrêtée qu'après un boss complété.");
        }

        JsonNode rules = gameDataService.runScoreRewards(run.getDataVersion());
        JsonNode scoreRules = rules.path("score");
        JsonNode tokenRules = rules.path("tokens");
        AppUser lockedUser = grantFinalTokens(run);
        long tokens = run.getRetirementTokens();

        run.setStatus(StsRunStatus.Retired);
        List<String> pvpUnlockedCardNames = stsPvpService.grantRunEndUnlocks(run);
        autoChallengeTriggerPublisher.publish(run.getOwner().getId(), AutoChallengeTrigger.STS_RUN_ENDED);
        return retirementResponse(run, tokens, lockedUser.getTokens(), scoreRules, tokenRules, pvpUnlockedCardNames);
    }

    @Transactional
    public StsRetreatPreviewResponse retreatPreview(AppUser owner, UUID runId) {
        StsRun run = ownedRunWithLock(owner, runId);
        AppUser lockedUser = userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())
            .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        JsonNode rules = gameDataService.runScoreRewards(run.getDataVersion());
        JsonNode scoreRules = rules.path("score");
        JsonNode tokenRules = rules.path("tokens");
        RetirementScoreBreakdown scoreBreakdown = retirementScore(run, scoreRules);
        long score = run.getRetirementScore() != null ? run.getRetirementScore() : scoreBreakdown.totalScore();
        long tokensPreview = run.getStatus() == StsRunStatus.Retired
            ? 0L
            : retirementTokens(score, tokenRules, Math.max(0, run.getAct()),
                    startingAct(run.getDataVersion()), maxActs(run.getDataVersion()));
        long projectedTokenBalance = Math.addExact(lockedUser.getTokens(), tokensPreview);
        return new StsRetreatPreviewResponse(
                true,
                run.getId(),
                run.getStatus(),
                score,
                tokensPreview,
                projectedTokenBalance,
                scoreBreakdown.visitedNodeScore(),
                scoreBreakdown.combatVictoryScore(),
                scoreBreakdown.eliteVictoryScore(),
                scoreBreakdown.eventVisitedScore(),
                scoreBreakdown.actReachedScore(),
                scoreBreakdown.relicOwnedScore(),
                scoreBreakdown.deckCardScore(),
                scoreBreakdown.goldOwnedScore(),
                scoreBreakdown.remainingHpPercentScore(),
                tokenRules.path("scorePerToken").asLong(0),
                tokenRules.path("rounding").asText(""),
                tokenRules.path("minimumReward").asLong(0)
        );
    }

    @Transactional
    public StsRunStateDto continueAfterRetreat(AppUser owner, UUID runId, int expectedAct) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if (run.getEnteredNodeId() != null || run.getActiveEncounter() != null || run.getActiveEvent() != null) {
            throw new ConflictException("La run STS ne peut pas continuer pendant un node.");
        }

        int currentAct = Math.max(0, run.getAct());
        if (currentAct > expectedAct) {
            return toDto(run);
        }
        if (currentAct < expectedAct) {
            throw new ConflictException("L'acte STS connu du client est obsolète.");
        }

        ObjectNode currentNode = node(run.getMapState(), run.getCurrentNodeId());
        if (!"Boss".equals(currentNode.path("type").asText())
                || !currentNode.path("completed").asBoolean(false)) {
            throw new ConflictException("La run STS ne peut continuer qu'après un boss complété.");
        }

        run.setAct(currentAct + 1);
        run.setCurrentFloor(0);
        run.setCurrentNodeId(0);
        run.setEnteredNodeId(null);
        run.setActiveEncounter(null);
        run.setActiveEvent(null);
        healToFull(run);
        run.setMapState(generateMap(gameDataService.mapGeneration(run.getDataVersion())));
        return toDto(run);
    }

    /**
     * Rend au joueur tous ses points de vie.
     *
     * <p>Un acte se franchit une seule fois, après un boss, et le suivant repart d'une carte
     * entièrement neuve : la santé laissée par le boss n'a plus rien à mesurer. Le soin est
     * décidé ici plutôt que déclaré par le client, pour la même raison que celui du feu de
     * camp — accepter des PV venus de l'extérieur revient à les offrir.
     */
    private void healToFull(StsRun run) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        if (player == null) {
            return;
        }
        player.put("currentHp", player.path("maxHp").asInt());
    }

    public StsRunStateDto toDto(StsRun run) {
        return new StsRunStateDto(
                run.getId(),
                run.getStatus().name(),
                run.getDataVersion(),
                run.getSelectedCharacter(),
                run.getAct(),
                run.getCurrentFloor(),
                run.getCurrentNodeId(),
                run.getEnteredNodeId(),
                apiJson(run.getPlayerState()),
                apiJson(run.getRunInventory()),
                apiJson(run.getMapState()),
                apiJson(unclaimedRewards(run)),
                apiJson(run.getActiveEncounter()),
                apiJson(run.getActiveCombat()),
                apiJson(run.getActiveEvent()),
                secretCardsUnlocked(run)
        );
    }

    /**
     * Replaces the run's player, deck, relics and encounter with a hand-picked set and builds the
     * combat the engine will fight, without going through the map.
     *
     * <p>{@code enteredNodeId} is deliberately left alone: this combat belongs to no node, so it
     * cannot be completed through {@link #completeNode} and the client is expected to leave it
     * without reporting a result. Entering a real node afterwards overwrites the encounter.
     */
    @Transactional
    public StsDebugCombatResponse startDebugCombat(AppUser owner, UUID runId, StsDebugCombatRequest request) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if (request == null || request.enemyIds().isEmpty()) {
            throw new BadRequestException("Au moins un ennemi est requis pour un combat de debug.");
        }

        if (run.getDataVersion() == null || run.getDataVersion().isBlank()) {
            run.setDataVersion(gameDataService.latestVersion());
        }

        String dataVersion = run.getDataVersion();
        String characterId = request.character() == null || request.character().isBlank()
                ? run.getSelectedCharacter()
                : request.character().trim();
        JsonNode character = gameDataService.character(dataVersion, characterId);
        int maxHp = request.maxHp() != null && request.maxHp() > 0
                ? request.maxHp()
                : character.path("hp").asInt(100);

        ArrayNode enemyCatalog = gameDataService.enemies(dataVersion);
        ObjectNode encounter = objectMapper.createObjectNode();
        encounter.put("encounterInstanceId", "encinst_debug_" + UUID.randomUUID());
        encounter.put("encounterId", "debug");
        ArrayNode enemyIds = encounter.putArray("enemyIds");
        for (String enemyId : request.enemyIds()) {
            enemyIds.add(requireDebugEnemyId(enemyCatalog, enemyId));
        }
        encounter.put("playerHpBefore", maxHp);
        encounter.put("act", run.getAct());
        encounter.put("floor", run.getCurrentFloor());
        encounter.put("startedAt", fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().toString());

        ObjectNode inventory = objectMapper.createObjectNode();
        inventory.put("gold", run.getRunInventory().path("gold").asInt(0));
        ArrayNode deck = inventory.putArray("deck");
        JsonNode cardCatalog = gameDataService.cards(dataVersion).path("cards");
        if (request.cardIds().isEmpty()) {
            addCatalogStartingCards(deck, cardCatalog, characterId);
        } else {
            for (String cardId : request.cardIds()) {
                addCardInstances(deck, requireDebugCard(cardCatalog, cardId), 1);
            }
        }
        ArrayNode relics = inventory.putArray("relics");
        for (String relicId : request.relicIds()) {
            ObjectNode relic = relics.addObject();
            relic.put("instanceId", "relicinst_" + UUID.randomUUID());
            relic.put("relicId", requireDebugRelicId(gameDataService.relics(dataVersion).path("relics"), relicId));
            relic.put("stage", 0);
        }

        run.setSelectedCharacter(characterId);
        run.setPlayerState(playerState(maxHp));
        run.setRunInventory(inventory);
        run.setActiveEvent(null);
        run.setActiveEncounter(encounter);
        run.setActiveCombat(createAuthoritativeCombat(run));

        return new StsDebugCombatResponse(
                true,
                run.getId(),
                apiJson(run.getPlayerState()),
                apiJson(run.getRunInventory()),
                apiJson(run.getActiveEncounter()),
                apiJson(run.getActiveCombat()));
    }

    /** Drops the hand-picked combat so the run can go back through its map. */
    @Transactional
    public void clearDebugCombat(AppUser owner, UUID runId) {
        StsRun run = ownedRunWithLock(owner, runId);
        run.setActiveEncounter(null);
        run.setActiveCombat(null);
        run.setActiveEvent(null);
    }

    private String requireDebugEnemyId(ArrayNode enemies, String requested) {
        String needle = requested == null ? "" : requested.trim();
        for (JsonNode enemy : enemies) {
            String id = enemy.path("id").asText("").trim();
            if (!id.isBlank()
                    && (id.equalsIgnoreCase(needle)
                        || enemy.path("enemyName").asText("").trim().equalsIgnoreCase(needle)
                        || enemy.path("displayName").asText("").trim().equalsIgnoreCase(needle))) {
                return id;
            }
        }
        throw new BadRequestException("Ennemi STS inconnu : " + needle);
    }

    private JsonNode requireDebugCard(JsonNode cards, String requested) {
        String needle = requested == null ? "" : requested.trim();
        for (JsonNode card : cards) {
            if (card.path("id").asText("").trim().equalsIgnoreCase(needle)
                    || card.path("cardName").asText("").trim().equalsIgnoreCase(needle)) {
                return card;
            }
        }
        throw new BadRequestException("Carte STS inconnue : " + needle);
    }

    private String requireDebugRelicId(JsonNode relicCatalog, String requested) {
        String needle = requested == null ? "" : requested.trim();
        for (JsonNode relic : relicCatalog) {
            String id = relic.path("id").asText("").trim();
            if (!id.isBlank()
                    && (id.equalsIgnoreCase(needle)
                        || relic.path("name").asText("").trim().equalsIgnoreCase(needle))) {
                return id;
            }
        }
        throw new BadRequestException("Relique STS inconnue : " + needle);
    }

    private JsonNode createAuthoritativeCombat(StsRun run) {
        boolean cold = !gameDataService.isCatalogLoaded(run.getDataVersion());
        io.micrometer.core.instrument.Timer timer =
                cold ? coldCombatCreationTimer : combatCreationTimer;
        JsonNode combat = timer.record(() -> stsAuthoritativeCombatService.createCombatState(run));
        if (combat == null || combat.isNull()) {
            throw new IllegalStateException("La création du combat STS authoritative a retourné un état vide.");
        }
        return combat;
    }

    private StsCompleteNodeResponse completeAuthoritativeCombatNode(
            StsRun run,
            int nodeId,
            StsAuthoritativeCombatService.AuthoritativeCompletionSnapshot snapshot
    ) {
        ObjectNode node = node(run.getMapState(), nodeId);
        // Max health first, current health second: everything downstream reads the ceiling to
        // judge the health under it, and a combat that raised the ceiling has to hand both back
        // or the gain dies with the combat that granted it.
        ((ObjectNode) run.getPlayerState()).put("maxHp", snapshot.playerMaxHpAfter());
        ((ObjectNode) run.getPlayerState()).put("currentHp", snapshot.playerHpAfter());

        if (!snapshot.victory()) {
            run.setStatus(StsRunStatus.Failed);
            autoChallengeTriggerPublisher.publish(run.getOwner().getId(), AutoChallengeTrigger.STS_RUN_ENDED);
            run.setEnteredNodeId(null);
            run.setActiveEncounter(null);
            run.setActiveCombat(null);
            run.setActiveEvent(null);
            stsPvpService.grantRunEndUnlocks(run);
            // Une défaite ne rapporte qu'une part de ce qu'un retrait aurait rendu, réglée par
            // tokens.gameOverMultiplier. À 0 — le défaut — elle ne rapporte toujours rien, et
            // seul le retrait volontaire après un boss complété paie.
            ObjectNode gameOverAccountPatch = objectMapper.createObjectNode();
            gameOverAccountPatch.put("tokenDelta", grantGameOverTokens(run));
            return completionResponse(run, objectMapper.createObjectNode(), gameOverAccountPatch);
        }

        ArrayNode createdRewards = generateRewards(run, node.path("type").asText());
        replacePendingRewards(run, createdRewards);

        node.put("completed", true);
        run.setCurrentNodeId(nodeId);
        run.setCurrentFloor(node.path("floor").asInt());
        run.setEnteredNodeId(null);
        run.setActiveEncounter(null);
        run.setActiveCombat(null);
        run.setActiveEvent(null);

        ObjectNode runInventoryPatch = objectMapper.createObjectNode();
        ObjectNode accountInventoryPatch = objectMapper.createObjectNode();
        accountInventoryPatch.put("tokenDelta", 0);
        return completionResponse(run, runInventoryPatch, accountInventoryPatch);
    }

    private Object apiJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private StsRun ownedRunWithLock(AppUser owner, UUID runId) {
        return stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())
                .orElseThrow(() -> new NotFoundException("Run STS introuvable."));
    }

    private void requireActive(StsRun run) {
        if (run.getStatus() != StsRunStatus.Active) {
            throw new ConflictException("Run STS inactive.");
        }
    }

    private ObjectNode playerState(int hp) {
        ObjectNode player = objectMapper.createObjectNode();
        player.put("maxHp", hp);
        player.put("currentHp", hp);
        return player;
    }

    private ObjectNode startingInventory(String dataVersion, String characterId) {
        ObjectNode inventory = objectMapper.createObjectNode();
        inventory.put("gold", 0);
        ArrayNode deck = inventory.putArray("deck");
        addCatalogStartingCards(deck, gameDataService.cards(dataVersion).path("cards"), characterId);

        ArrayNode relics = inventory.putArray("relics");
        ObjectNode relic = objectMapper.createObjectNode();
        relic.put("instanceId", "relicinst_" + UUID.randomUUID());
        relic.put("relicId", characterId + "Relic");
        relic.put("stage", 0);
        relics.add(relic);
        return inventory;
    }

    private void addCatalogStartingCards(ArrayNode deck, JsonNode cardsCatalog, String characterId) {
        for (JsonNode card : cardsCatalog) {
            int startingCount = card.path("startingCount").asInt(0);
            if (startingCount <= 0 || !isAllowedForCharacter(card, characterId)) {
                continue;
            }
            addCardInstances(deck, card, startingCount);
        }
    }

    private void addCardInstances(ArrayNode deck, JsonNode card, int count) {
        for (int i = 0; i < count; i++) {
            ObjectNode cardInstance = objectMapper.createObjectNode();
            cardInstance.put("instanceId", "cardinst_" + UUID.randomUUID());
            cardInstance.put("cardId", card.path("id").asText());
            cardInstance.put("targetingMode", card.path("targetingMode").asText("Enemy"));
            cardInstance.set("enchantments", objectMapper.createArrayNode());
            deck.add(cardInstance);
        }
    }

    private ObjectNode generateMap(JsonNode config) {
        ObjectNode map = objectMapper.createObjectNode();
        ArrayNode nodes = map.putArray("nodes");
        ObjectNode start = objectMapper.createObjectNode();
        start.put("id", 0);
        start.put("floor", config.path("startNode").path("floor").asInt(0));
        start.put("type", config.path("startNode").path("type").asText("Start"));
        start.put("posX", config.path("startNode").path("posX").asDouble(0.5));
        start.put("visited", config.path("startNode").path("visited").asBoolean(true));
        start.put("completed", config.path("startNode").path("completed").asBoolean(true));
        start.set("nextIds", objectMapper.createArrayNode());
        nodes.add(start);

        int nextId = 1;
        List<List<ObjectNode>> floors = new ArrayList<>();
        floors.add(List.of(start));
        int height = config.path("height").asInt(12);
        for (int floor = 1; floor < height; floor++) {
            int nodeCount = randomBetween(nodeCountMin(config, floor), nodeCountMax(config, floor) + 1);
            List<ObjectNode> floorNodes = new ArrayList<>();
            for (int index = 0; index < nodeCount; index++) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", nextId++);
                node.put("floor", floor);
                node.put("type", forcedType(config, floor, height));
                node.put("posX", nodeCount == 1 ? 0.5 : (double) index / (nodeCount - 1));
                node.put("visited", false);
                node.put("completed", false);
                node.set("nextIds", objectMapper.createArrayNode());
                nodes.add(node);
                floorNodes.add(node);
            }
            floors.add(floorNodes);
        }

        for (int floor = 0; floor < floors.size() - 1; floor++) {
            List<ObjectNode> previousFloor = floors.get(floor);
            List<ObjectNode> currentFloor = floors.get(floor + 1);
            for (ObjectNode previous : previousFloor) {
                ObjectNode target = nearestNode(previous, currentFloor);
                ((ArrayNode) previous.get("nextIds")).add(target.path("id").asInt());
            }
            for (ObjectNode current : currentFloor) {
                if (!hasIncoming(previousFloor, current.path("id").asInt())) {
                    ObjectNode source = nearestNode(current, previousFloor);
                    addNextId(source, current.path("id").asInt());
                }
            }
        }

        return map;
    }

    private int nodeCountMin(JsonNode config, int floor) {
        return nodeCountRule(config, floor).path("minInclusive").asInt(1);
    }

    private int nodeCountMax(JsonNode config, int floor) {
        return nodeCountRule(config, floor).path("maxInclusive").asInt(1);
    }

    private JsonNode nodeCountRule(JsonNode config, int floor) {
        for (JsonNode rule : config.path("nodeCountsByFloor")) {
            if (rule.has("floor") && rule.path("floor").asInt() == floor) {
                return rule;
            }
            JsonNode range = rule.path("floorRange");
            if (!range.isMissingNode()
                    && floor >= range.path("fromInclusive").asInt()
                    && floor < range.path("toExclusive").asInt()) {
                return rule;
            }
        }
        return objectMapper.createObjectNode().put("minInclusive", 1).put("maxInclusive", 1);
    }

    private String forcedType(JsonNode config, int floor, int height) {
        JsonNode rule = nodeCountRule(config, floor);
        if (rule.hasNonNull("forcedType")) {
            return rule.path("forcedType").asText();
        }
        JsonNode weights = floor == height - 1
                ? config.path("nodeTypeWeights").path("bossFloor")
                : config.path("nodeTypeWeights").path("default");
        return weightedKey(weights);
    }

    private ObjectNode nearestNode(ObjectNode source, List<ObjectNode> targets) {
        ObjectNode nearest = targets.getFirst();
        double sourceX = source.path("posX").asDouble();
        double distance = Double.MAX_VALUE;
        for (ObjectNode target : targets) {
            double candidate = Math.abs(sourceX - target.path("posX").asDouble());
            if (candidate < distance) {
                nearest = target;
                distance = candidate;
            }
        }
        return nearest;
    }

    private boolean hasIncoming(List<ObjectNode> sources, int nodeId) {
        for (ObjectNode source : sources) {
            for (JsonNode nextId : source.path("nextIds")) {
                if (nextId.asInt() == nodeId) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addNextId(ObjectNode source, int nodeId) {
        ArrayNode nextIds = (ArrayNode) source.get("nextIds");
        for (JsonNode nextId : nextIds) {
            if (nextId.asInt() == nodeId) {
                return;
            }
        }
        nextIds.add(nodeId);
    }

    private void requireReachable(StsRun run, int nodeId) {
        ObjectNode currentNode = node(run.getMapState(), run.getCurrentNodeId());
        for (JsonNode nextId : currentNode.path("nextIds")) {
            if (nextId.asInt() == nodeId) {
                return;
            }
        }
        throw new ConflictException("Node STS non atteignable.");
    }

    private ObjectNode node(JsonNode map, int nodeId) {
        for (JsonNode node : map.path("nodes")) {
            if (node.path("id").asInt() == nodeId && node instanceof ObjectNode objectNode) {
                return objectNode;
            }
        }
        throw new NotFoundException("Node STS introuvable.");
    }

    private JsonNode createEncounter(StsRun run, int nodeId, String nodeType) {
        ObjectNode currentNode = node(run.getMapState(), nodeId);
        int floor = currentNode.path("floor").asInt(run.getCurrentFloor());
        JsonNode entry = chooseEncounterEntry(gameDataService.enemyPool(run.getDataVersion()), nodeType, run.getAct(), floor);
        ObjectNode encounter = objectMapper.createObjectNode();
        encounter.put("encounterInstanceId", "encinst_" + UUID.randomUUID());
        encounter.put("encounterId", entry.path("encounterId").asText());
        ArrayNode enemyIds = encounter.putArray("enemyIds");
        entry.path("enemyIds").forEach(enemyIds::add);
        encounter.put("playerHpBefore", run.getPlayerState().path("currentHp").asInt());
        encounter.put("act", run.getAct());
        encounter.put("floor", floor);
        encounter.put("startedAt", fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().toString());
        return encounter;
    }

    private JsonNode chooseEncounterEntry(JsonNode enemyPool, String nodeType, int act, int floor) {
        ArrayNode candidates = objectMapper.createArrayNode();
        for (JsonNode enemyEntry : enemyPool.path("enemies")) {
            if (enemyEntry == null || enemyEntry.isNull()) {
                continue;
            }

            if (!matchesEncounterType(enemyEntry, nodeType)) {
                continue;
            }

            if (isOutsideEncounterRange(enemyEntry.path("minFloor"), enemyEntry.path("maxFloor"), floor)) {
                continue;
            }

            if (isOutsideEncounterRange(enemyEntry.path("minAct"), enemyEntry.path("maxAct"), act)) {
                continue;
            }

            candidates.add(enemyEntry);
        }

        if (candidates.isEmpty()) {
            throw new BadRequestException("Table d'encounter STS introuvable pour cette floor/act.");
        }

        return weightedEntry(candidates);
    }

    private void validateCompletion(StsRun run, String nodeType, StsCompleteNodeRequest request) {
        if (!ENCOUNTER_NODE_TYPES.contains(nodeType)) {
            return;
        }
        if (request == null) {
            throw new BadRequestException("Résultat de combat STS requis.");
        }
        JsonNode encounter = run.getActiveEncounter();
        if (encounter == null || encounter.isNull()) {
            throw new ConflictException("Aucun encounter STS actif.");
        }
        if (!encounter.path("encounterInstanceId").asText().equals(request.encounterInstanceId())) {
            throw new ConflictException("Encounter STS obsolète.");
        }
        if (request.turnCount() == null || request.turnCount() <= 0) {
            throw new BadRequestException("Le nombre de tours STS est invalide.");
        }
        if (request.damageTaken() == null || request.damageTaken() < 0) {
            throw new BadRequestException("Les dégâts reçus STS sont invalides.");
        }
        int maxHp = run.getPlayerState().path("maxHp").asInt();
        if (request.playerHpAfter() == null || request.playerHpAfter() < 0 || request.playerHpAfter() > maxHp) {
            throw new BadRequestException("Les HP STS restants sont invalides.");
        }
        if (isVictory(request.result()) && !sameEnemies(encounter.path("enemyIds"), request.enemiesDefeated())) {
            throw new BadRequestException("Les ennemis vaincus STS ne correspondent pas à l'encounter.");
        }
    }

    private boolean isVictory(String result) {
        return result != null && RESULT_VICTORY.equalsIgnoreCase(result.trim());
    }

    private boolean matchesEncounterType(JsonNode enemyEntry, String nodeType) {
        return switch (nodeType) {
            case "Elite" -> enemyEntry.path("elite").asBoolean(false);
            case "Boss" -> enemyEntry.path("boss").asBoolean(false);
            default -> !enemyEntry.path("elite").asBoolean(false) && !enemyEntry.path("boss").asBoolean(false);
        };
    }

    private boolean isOutsideEncounterRange(JsonNode minToken, JsonNode maxToken, int value) {
        int min = minToken == null || minToken.isMissingNode() ? -1 : minToken.asInt(-1);
        int max = maxToken == null || maxToken.isMissingNode() ? -1 : maxToken.asInt(-1);
        return (min >= 0 && value < min) || (max >= 0 && value > max);
    }

    private boolean sameEnemies(JsonNode expectedEnemies, List<String> actualEnemies) {
        if (actualEnemies == null) {
            return false;
        }
        Map<String, Integer> expectedCounts = new HashMap<>();
        int expectedSize = 0;
        for (JsonNode enemy : expectedEnemies) {
            expectedCounts.merge(enemy.asText(), 1, Integer::sum);
            expectedSize++;
        }
        if (expectedSize != actualEnemies.size()) {
            return false;
        }
        for (String enemy : actualEnemies) {
            Integer count = expectedCounts.get(enemy);
            if (count == null || count == 0) {
                return false;
            }
            expectedCounts.put(enemy, count - 1);
        }
        return true;
    }

    private ArrayNode generateRewards(StsRun run, String nodeType) {
        ArrayNode rewards = objectMapper.createArrayNode();
        if (!ENCOUNTER_NODE_TYPES.contains(nodeType)) {
            return rewards;
        }
        rewards.add(goldReward(run.getDataVersion(), nodeType));
        rewards.add(cardChoiceReward(run, nodeType, true));
        if ("Elite".equals(nodeType) || "Boss".equals(nodeType)) {
            ObjectNode relicReward = relicReward(run, nodeType);
            if (relicReward != null) {
                rewards.add(relicReward);
            }
        }
        return rewards;
    }

    private ObjectNode goldReward(String dataVersion, String nodeType) {
        JsonNode gold = gameDataService.rewardTables(dataVersion).path("gold");
        int amount = randomBetween(gold.path("baseMinInclusive").asInt(15), gold.path("baseMaxInclusive").asInt(25) + 1);
        if ("Elite".equals(nodeType)) {
            amount += gold.path("eliteBonus").asInt(0);
        } else if ("Boss".equals(nodeType)) {
            amount += gold.path("bossBonus").asInt(0);
        }
        ObjectNode reward = rewardBase("Gold");
        reward.put("amount", amount);
        return reward;
    }

    private ObjectNode cardChoiceReward(StsRun run, String nodeType, boolean includeEnemyRewardCards) {
        return cardChoiceReward(run, nodeType, includeEnemyRewardCards, null);
    }

    /**
     * @param profile the pool an event asked for, or null for the node's ordinary reward
     */
    private ObjectNode cardChoiceReward(
            StsRun run, String nodeType, boolean includeEnemyRewardCards,
            EventCardRewardProfile profile) {
        String dataVersion = run.getDataVersion();
        JsonNode rewardTables = gameDataService.rewardTables(dataVersion);
        int choicesPerReward = rewardTables.path("cardChoices").path("choicesPerReward").asInt(3);
        if (profile != null) {
            choicesPerReward = profile.choiceCount(choicesPerReward);
        }
        List<WeightedCardChoice> candidates =
                cardCandidates(run, nodeType, includeEnemyRewardCards, rewardTables, profile);
        ObjectNode reward = rewardBase("CardChoice");
        ArrayNode choices = reward.putArray("choices");
        Set<String> selectedCardIds = new HashSet<>();
        boolean elite = "Elite".equals(nodeType);
        for (int i = 0; i < choicesPerReward && !candidates.isEmpty(); i++) {
            WeightedCardChoice picked = weightedCardChoice(candidates, selectedCardIds);
            if (picked == null || picked.card() == null) {
                break;
            }

            JsonNode card = picked.card();
            String cardId = card.path("id").asText();
            if (cardId.isBlank() || !selectedCardIds.add(cardId)) {
                continue;
            }
            ObjectNode choice = objectMapper.createObjectNode();
            choice.put("cardId", cardId);
            ArrayNode enchantments = choice.putArray("enchantments");
            JsonNode eliteEnchant = rewardTables.path("cardChoices").path("eliteCardEnchant");
            if (elite && eliteEnchant.path("enabled").asBoolean(false)) {
                int minimum = Math.max(1, eliteEnchant.path("minInclusive").asInt(1));
                int maximum = Math.max(minimum, eliteEnchant.path("maxInclusive").asInt(minimum));
                rollEliteEnchantment(run, cardId, randomBetween(minimum, maximum + 1), enchantments);
            }
            choices.add(choice);
        }
        return reward;
    }

    /**
     * Enchante pour de bon la carte qu'une elite propose.
     *
     * <p>On ecrivait ici un « EliteEnchantment », qui n'est le nom d'aucun enchantement : le
     * moteur l'ignore -- {@code EnchantmentEngine} ne lit que ceux dont il tient un handler --
     * et le client, ne sachant pas le lire non plus, en tirait un au hasard pour le seul
     * affichage. La carte se montrait donc enchantee dans la recompense et entrait au deck sans
     * rien. Le tirage se fait maintenant ici, par la table qui sert deja au feu de peu : ce qui
     * est propose est ce qui est gagne.
     *
     * <p>Une carte dont ce moteur ne sait pas batir la definition est proposee sans enchantement
     * plutot que de faire echouer la recompense entiere.
     */
    private void rollEliteEnchantment(
            StsRun run, String cardId, int charges, ArrayNode enchantments) {
        CardDefinition definition;
        try {
            CardCatalog catalog = cardCatalogFactory.create(
                    gameDataService.cards(run.getDataVersion()),
                    run.getDataVersion(),
                    List.of(cardId),
                    run.getSelectedCharacter());
            definition = catalog.cards().get(cardId);
        } catch (RuntimeException unreadable) {
            definition = null;
        }
        if (definition == null) {
            return;
        }
        EnchantmentRoller.Roll roll = EnchantmentRoller.roll(
                new CardInstance("cardinst_reward_" + cardId, cardId, List.of()),
                definition,
                Math.max(1, charges),
                new RandomState(random.nextLong()));
        for (EnchantmentInstance enchantment : roll.instance().enchantments()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("enchantmentId", enchantment.enchantmentId());
            node.put("level", enchantment.level());
            enchantments.add(node);
        }
    }

    private List<WeightedCardChoice> cardCandidates(StsRun run, String nodeType, boolean includeEnemyRewardCards, JsonNode rewardTables) {
        return cardCandidates(run, nodeType, includeEnemyRewardCards, rewardTables, null);
    }

    private List<WeightedCardChoice> cardCandidates(StsRun run, String nodeType, boolean includeEnemyRewardCards, JsonNode rewardTables, EventCardRewardProfile profile) {
        String dataVersion = run.getDataVersion();
        String characterId = run.getSelectedCharacter();
        int act = authoredAct(run);
        Map<String, Integer> rarityWeights = cardRarityWeightsForNode(rewardTables, nodeType, act);
        boolean hasRarityWeights = !rarityWeights.isEmpty();
        double selectedCharacterWeight = cardSelectedCharacterWeightForNode(rewardTables, nodeType, act);

        boolean secretsUnlocked = secretCardsUnlocked(run);

        Map<String, WeightedCardChoice> allEligibleById = new HashMap<>();
        Map<String, WeightedCardChoice> candidatesById = new HashMap<>();
        for (JsonNode card : gameDataService.cards(dataVersion).path("cards")) {
            // Une carte secrète n'existe pour personne avant que le compte n'ait ouvert le pool,
            // et la question se pose avant celle du profil : un event qui demande « une carte de
            // telle rareté » n'a pas à pouvoir contourner le secret.
            if (!secretsUnlocked && isSecretCard(card)) {
                continue;
            }
            // An event's profile answers for itself which cards belong in its pool; the ordinary
            // reward keeps the node's own rule.
            boolean eligible = profile != null
                    ? profile.accepts(card, characterId)
                    : isEligibleRewardCard(card, characterId);
            if (eligible) {
                String cardId = card.path("id").asText();
                if (!cardId.isBlank()) {
                    allEligibleById.put(cardId, new WeightedCardChoice(card, 1));
                    int baseWeight = hasRarityWeights
                            ? resolveConfiguredRarityWeight(rarityWeights, cardRarityToken(card))
                            : 1;
                    if (selectedCharacterWeight != 1.0 && favorsSelectedCharacter(card, characterId)) {
                        baseWeight = (int) Math.round(baseWeight * selectedCharacterWeight);
                    }
                    if (profile != null) {
                        baseWeight = profile.weight(card, baseWeight);
                    }
                    if (baseWeight > 0) {
                        candidatesById.put(cardId, new WeightedCardChoice(card, baseWeight));
                    }
                }
            }
        }

        if (hasRarityWeights && candidatesById.isEmpty()
                && !hasKnownCardRarityWeight(rarityWeights)) {
            if ("Boss".equals(nodeType)) {
                candidatesById.putAll(highestRarityFallbackCards(allEligibleById));
            } else {
                candidatesById.putAll(allEligibleById);
            }
        }

        if (!includeEnemyRewardCards) {
            return new ArrayList<>(candidatesById.values());
        }

        int enemyRewardWeight = itiEnemyRewardWeight(run);
        if (enemyRewardWeight <= 0) {
            return new ArrayList<>(candidatesById.values());
        }

        Set<String> enemyRewardCardIds = enemyRewardCardIds(run);
        for (String enemyCardId : enemyRewardCardIds) {
            WeightedCardChoice current = candidatesById.get(enemyCardId);
            if (current == null) {
                continue;
            }

            int boostedWeight = Math.max(1, current.weight()) + enemyRewardWeight;
            candidatesById.put(enemyCardId, new WeightedCardChoice(current.card(), boostedWeight));
        }

        return new ArrayList<>(candidatesById.values());
    }

    private WeightedCardChoice weightedCardChoice(List<WeightedCardChoice> candidates, Set<String> alreadySelectedCardIds) {
        int totalWeight = 0;
        for (WeightedCardChoice candidate : candidates) {
            if (candidate == null || candidate.card() == null) {
                continue;
            }
            String cardId = candidate.card().path("id").asText();
            if (cardId.isBlank() || alreadySelectedCardIds.contains(cardId)) {
                continue;
            }
            totalWeight += Math.max(0, candidate.weight());
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (WeightedCardChoice candidate : candidates) {
            if (candidate == null || candidate.card() == null) {
                continue;
            }
            String cardId = candidate.card().path("id").asText();
            if (cardId.isBlank() || alreadySelectedCardIds.contains(cardId)) {
                continue;
            }

            cursor += Math.max(0, candidate.weight());
            if (roll < cursor) {
                return candidate;
            }
        }

        return null;
    }

    private int itiEnemyRewardWeight(StsRun run) {
        for (JsonNode relicNode : run.getRunInventory().path("relics")) {
            if (relicNode == null || relicNode.isNull()) {
                continue;
            }

            String relicId = relicNode.path("relicId").asText("");
            if (!ITI_RELIC_ID.equalsIgnoreCase(relicId)) {
                continue;
            }

            int stage = relicNode.path("stage").asInt(0);
            return switch (stage) {
                case 0 -> 100;
                case 1 -> 200;
                default -> 0;
            };
        }

        return 0;
    }

    private Set<String> enemyRewardCardIds(StsRun run) {
        JsonNode activeEncounter = run.getActiveEncounter();
        if (activeEncounter == null || activeEncounter.isNull()) {
            return Set.of();
        }

        Set<String> encounteredEnemyIds = new HashSet<>();
        for (JsonNode enemyIdNode : activeEncounter.path("enemyIds")) {
            String enemyId = enemyIdNode.asText("").trim();
            if (!enemyId.isBlank()) {
                encounteredEnemyIds.add(enemyId);
            }
        }

        if (encounteredEnemyIds.isEmpty()) {
            return Set.of();
        }

        Set<String> rewardCardIds = new HashSet<>();
        for (JsonNode enemy : gameDataService.enemies(run.getDataVersion())) {
            String enemyId = enemy.path("id").asText("").trim();
            String enemyName = enemy.path("enemyName").asText("").trim();
            if (!encounteredEnemyIds.contains(enemyId) && !encounteredEnemyIds.contains(enemyName)) {
                continue;
            }

            for (JsonNode rewardCardIdNode : enemy.path("rewardCardIds")) {
                String rewardCardId = rewardCardIdNode.asText("").trim();
                if (!rewardCardId.isBlank()) {
                    rewardCardIds.add(rewardCardId);
                }
            }
        }

        return rewardCardIds;
    }

    private boolean isEligibleRewardCard(JsonNode card, String characterId) {
        if (card == null || card.isNull() || !isAllowedForCharacter(card, characterId)) {
            return false;
        }

        String favored = card.path("favoredCharacter").asText("").trim();
        if ("Starting".equalsIgnoreCase(favored) || "Impossible".equalsIgnoreCase(favored)) {
            return false;
        }

        JsonNode tags = card.path("tags");
        if (containsTextIgnoreCase(tags, "Created") || containsTextIgnoreCase(tags, "Unobtainable")) {
            return false;
        }

        return true;
    }

    /**
     * Si le compte de cette run a déjà ouvert le pool de cartes secrètes.
     *
     * <p>Le déverrouillage appartient au compte, pas à la run : une carte {@code SecretUnlock}
     * jouée une fois vaut pour toutes les parties suivantes, y compris celles d'un autre
     * personnage.
     */
    private boolean secretCardsUnlocked(StsRun run) {
        AppUser owner = run == null ? null : run.getOwner();
        return owner != null && owner.hasUnlockedStsSecretCards();
    }

    /**
     * Si cette carte fait partie du pool secret.
     *
     * <p>Le marqueur se compare entier, et non par sous-chaîne : {@code SecretUnlock} est la carte
     * qui ouvre le pool, pas une carte du pool. Les enfermer ensemble laisserait la clé derrière
     * la porte qu'elle ouvre, et plus rien ne pourrait jamais l'ouvrir.
     */
    private boolean isSecretCard(JsonNode card) {
        for (JsonNode tag : card.path("tags")) {
            if ("Secret".equalsIgnoreCase(tag.asText("").trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedForCharacter(JsonNode card, String characterId) {
        String favored = card.path("favoredCharacter").asText("");
        return favored.isBlank()
                || "Aucun".equals(favored)
                || "Neutral".equals(favored)
                || "Starting".equals(favored)
                || (characterId != null && characterId.equals(favored));
    }

    private boolean favorsSelectedCharacter(JsonNode card, String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return false;
        }
        return characterId.equals(card.path("favoredCharacter").asText(""));
    }

    private ObjectNode relicReward(StsRun run, String nodeType) {
        JsonNode rewardTables = gameDataService.rewardTables(run.getDataVersion());
        Map<String, Integer> rarityWeights = relicRarityWeightsForNode(rewardTables, nodeType, authoredAct(run));
        boolean useConfiguredWeights = !rarityWeights.isEmpty();
        boolean bossNode = "Boss".equals(nodeType);

        List<JsonNode> candidates = relicCandidates(run);
        if (candidates.isEmpty()) {
            candidates = relicCandidatesIncludingOwned(run);
            if (candidates.isEmpty()) {
                return null;
            }
        }

        if (bossNode) {
            List<JsonNode> bossCandidates = candidates.stream().filter(this::isBossTierRelic).toList();
            if (!bossCandidates.isEmpty()) {
                candidates = bossCandidates;
            } else {
                List<JsonNode> ownedBossCandidates = relicCandidatesIncludingOwned(run).stream()
                        .filter(this::isBossTierRelic)
                        .toList();
                if (!ownedBossCandidates.isEmpty()) {
                    candidates = ownedBossCandidates;
                }
            }
        } else {
            candidates = candidates.stream().filter(relic -> !isBossTierRelic(relic)).toList();
            if (candidates.isEmpty()) {
                candidates = relicCandidatesIncludingOwned(run).stream().filter(relic -> !isBossTierRelic(relic)).toList();
                if (candidates.isEmpty()) {
                    return null;
                }
            }
        }

        if (useConfiguredWeights) {
            List<WeightedJsonNode> weightedCandidates = weightedByConfiguredRelicRarity(candidates, rarityWeights);
            if (!weightedCandidates.isEmpty()) {
                ObjectNode reward = rewardBase("Relic");
                reward.put("relicId", pickWeightedJsonNode(weightedCandidates).path("id").asText());
                return reward;
            }

            if (bossNode) {
                List<WeightedJsonNode> weightedOwnedCandidates = weightedByConfiguredRelicRarity(relicCandidatesIncludingOwned(run), rarityWeights);
                if (!weightedOwnedCandidates.isEmpty()) {
                    ObjectNode reward = rewardBase("Relic");
                    reward.put("relicId", pickWeightedJsonNode(weightedOwnedCandidates).path("id").asText());
                    return reward;
                }
            }
        }

        List<JsonNode> selectedPool;
        if (bossNode) {
            selectedPool = candidates.isEmpty() ? highestRarityRelicCandidates(relicCandidatesIncludingOwned(run)) : candidates;
        } else if ("Elite".equals(nodeType)) {
            selectedPool = candidates;
        } else {
            selectedPool = candidates;
        }

        ObjectNode reward = rewardBase("Relic");
        reward.put("relicId", selectedPool.get(random.nextInt(selectedPool.size())).path("id").asText());
        return reward;
    }

    private List<WeightedJsonNode> weightedByConfiguredRelicRarity(List<JsonNode> candidates, Map<String, Integer> rarityWeights) {
        List<WeightedJsonNode> weighted = new ArrayList<>();
        for (JsonNode relic : candidates) {
            int weight = resolveConfiguredRelicRarityWeight(rarityWeights, relic);
            if (weight > 0) {
                weighted.add(new WeightedJsonNode(relic, weight));
            }
        }
        return weighted;
    }

    private JsonNode pickWeightedJsonNode(List<WeightedJsonNode> weightedEntries) {
        int totalWeight = 0;
        for (WeightedJsonNode entry : weightedEntries) {
            totalWeight += Math.max(0, entry.weight());
        }

        if (totalWeight <= 0) {
            return weightedEntries.get(0).node();
        }

        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (WeightedJsonNode entry : weightedEntries) {
            cursor += Math.max(0, entry.weight());
            if (roll < cursor) {
                return entry.node();
            }
        }

        return weightedEntries.get(0).node();
    }

    private List<JsonNode> relicCandidates(StsRun run) {
        Set<String> ownedRelicIds = new HashSet<>();
        run.getRunInventory().path("relics").forEach(relic -> ownedRelicIds.add(relic.path("relicId").asText()));

        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode relic : gameDataService.relics(run.getDataVersion()).path("relics")) {
            if (relic.path("backendGrantable").asBoolean(false) && !ownedRelicIds.contains(relic.path("id").asText())) {
                candidates.add(relic);
            }
        }
        return candidates;
    }

    private List<JsonNode> relicCandidatesIncludingOwned(StsRun run) {
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode relic : gameDataService.relics(run.getDataVersion()).path("relics")) {
            if (relic.path("backendGrantable").asBoolean(false)) {
                candidates.add(relic);
            }
        }
        return candidates;
    }

    private boolean isBossTierRelic(JsonNode relic) {
        if (relic.path("boss").asBoolean(false)) {
            return true;
        }

        if (isExplicitBossRelic(relic)) {
            return true;
        }

        if (containsTextIgnoreCase(relic.path("nodeTypes"), "Boss")) {
            return true;
        }

        if (containsTextIgnoreCase(relic.path("tags"), "Boss")
                || containsTextIgnoreCase(relic.path("types"), "Boss")
                || containsTextIgnoreCase(relic.path("categories"), "Boss")) {
            return true;
        }

        String tierToken = firstNonBlankText(relic, "rarity", "tier", "pool", "relicRarity", "relicTier");
        if (tierToken.isBlank()) {
            return false;
        }

        String normalizedTier = normalizeWeightKey(tierToken);
        return normalizedTier.contains("boss")
                || "bossrelic".equals(normalizedTier)
                || "legendary".equals(normalizedTier)
                || "mythic".equals(normalizedTier);
    }

    static String firstNonBlankText(JsonNode source, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = source.path(fieldName);
            if (!valueNode.isTextual()) {
                continue;
            }

            String value = valueNode.asText().trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String cardRarityToken(JsonNode card) {
        return firstNonBlankText(card, "rarity", "cardRarity", "tier", "pool");
    }

    private String relicRarityToken(JsonNode relic) {
        return firstNonBlankText(relic, "rarity", "relicRarity", "tier", "pool", "relicTier");
    }

    /**
     * Les poids de rareté qui gouvernent les cartes proposées après ce type de nœud.
     *
     * <p>Une table qui ne nomme pas le nœud ne le laisse pas sans pondération : on retombe sur
     * celle du combat ordinaire. Sans ce repli, une table écrite comme une liste de {@code
     * tables} dont aucune entrée ne cite le boss rendait un ensemble vide, et
     * {@code cardCandidates} donnait alors le même poids à toutes les cartes — la table était
     * lue pour un combat et ignorée juste après le boss, là où les raretés comptent le plus.
     *
     * <p>Le repli s'arrête au combat lui-même, faute de quoi il tournerait en rond.
     */
    private Map<String, Integer> cardRarityWeightsForNode(JsonNode rewardTables, String nodeType, int act) {
        Map<String, Integer> weights =
                rewardRarityWeightsForNode(rewardTables, nodeType, act, "cardChoices", "cardRewards", "cards");
        if (!weights.isEmpty() || DEFAULT_REWARD_NODE_TYPE.equals(nodeType)) {
            return weights;
        }
        return rewardRarityWeightsForNode(
                rewardTables, DEFAULT_REWARD_NODE_TYPE, act, "cardChoices", "cardRewards", "cards");
    }

    private Map<String, Integer> relicRarityWeightsForNode(JsonNode rewardTables, String nodeType, int act) {
        return rewardRarityWeightsForNode(rewardTables, nodeType, act, "relics", "relicRewards", "relic");
    }

    /**
     * Optional multiplier applied to a card's rarity weight when it favors the run's selected
     * character, e.g. {@code selectedCharacterWeight: 2} doubles that card's odds. Defaults to 1
     * (no bias) when the table does not configure it.
     */
    private double cardSelectedCharacterWeightForNode(JsonNode rewardTables, String nodeType, int act) {
        for (String sectionKey : new String[] { "cardChoices", "cardRewards", "cards" }) {
            JsonNode section = rewardTables.path(sectionKey);
            if (section.isMissingNode() || section.isNull()) {
                continue;
            }

            for (JsonNode scope : actScopes(section, act)) {
                JsonNode nodeConfig = findNodeConfig(scope, nodeType);
                for (JsonNode candidate : actScopes(nodeConfig, act)) {
                    if (candidate.has("selectedCharacterWeight")) {
                        return Math.max(0, candidate.path("selectedCharacterWeight").asDouble(1));
                    }
                }
                if (scope.has("selectedCharacterWeight")) {
                    return Math.max(0, scope.path("selectedCharacterWeight").asDouble(1));
                }
            }
        }

        return 1.0;
    }

    private Map<String, Integer> rewardRarityWeightsForNode(
            JsonNode rewardTables, String nodeType, int act, String... sectionKeys) {
        for (String sectionKey : sectionKeys) {
            JsonNode section = rewardTables.path(sectionKey);
            if (section.isMissingNode() || section.isNull()) {
                continue;
            }

            for (JsonNode scope : actScopes(section, act)) {
                JsonNode nodeConfig = findNodeConfig(scope, nodeType);
                Map<String, Integer> nodeWeights = extractRarityWeights(nodeConfig, nodeType, act);
                if (!nodeWeights.isEmpty()) {
                    return nodeWeights;
                }

                Map<String, Integer> sectionWeights = extractRarityWeights(scope, nodeType, act);
                if (!sectionWeights.isEmpty()) {
                    return sectionWeights;
                }
            }
        }

        return Map.of();
    }

    private JsonNode findNodeConfig(JsonNode section, String nodeType) {
        if (section == null || section.isNull() || section.isMissingNode()) {
            return objectMapper.createObjectNode();
        }

        String lowerNodeType = nodeType == null ? "" : nodeType.toLowerCase();

        JsonNode tables = section.path("tables");
        if (tables.isArray()) {
            for (JsonNode table : tables) {
                if (tableAppliesToNodeType(table, nodeType, lowerNodeType)) {
                    return table;
                }
            }
        }

        JsonNode byNodeType = section.path("byNodeType");
        if (byNodeType.isObject()) {
            JsonNode exact = byNodeType.path(nodeType);
            if (!exact.isMissingNode() && !exact.isNull()) {
                return exact;
            }

            JsonNode lower = byNodeType.path(lowerNodeType);
            if (!lower.isMissingNode() && !lower.isNull()) {
                return lower;
            }
        }

        for (String key : new String[] { nodeType, lowerNodeType, "Normal", "normal", "default", "Default", "combat", "Combat" }) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonNode node = section.path(key);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }

        return section;
    }

    private boolean tableAppliesToNodeType(JsonNode table, String nodeType, String lowerNodeType) {
        if (table == null || table.isNull()) {
            return false;
        }

        if (containsTextIgnoreCase(table.path("nodeTypes"), nodeType)
                || containsTextIgnoreCase(table.path("nodeTypes"), lowerNodeType)
                || containsTextIgnoreCase(table.path("nodeType"), nodeType)
                || containsTextIgnoreCase(table.path("nodeType"), lowerNodeType)) {
            return true;
        }

        if ("Boss".equals(nodeType) && containsTextIgnoreCase(table.path("kind"), "boss")) {
            return true;
        }

        if (!"Boss".equals(nodeType)
                && (containsTextIgnoreCase(table.path("kind"), "normal")
                || containsTextIgnoreCase(table.path("kind"), "combat"))) {
            return true;
        }

        return false;
    }

    private Map<String, Integer> extractRarityWeights(JsonNode node, String nodeType, int act) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }

        for (String containerKey : new String[] { "rarityWeights", "rarity_weights", "weights", "cardRarityWeights", "relicRarityWeights" }) {
            JsonNode container = node.path(containerKey);
            for (JsonNode scoped : actScopes(container, act)) {
                Map<String, Integer> parsed = parseWeightMap(weightsForNodeKind(scoped, nodeType));
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        }

        return Map.of();
    }

    /**
     * The weight table that applies to this kind of node, when the authored one is split by kind.
     *
     * <p>The card table writes its weights as {@code rarityWeights: { normal: {...}, boss: {...} }},
     * and nothing here understood that shape: {@code parseWeightMap} takes numbers, or objects
     * carrying a {@code weight} field, and a nested table of rarities is neither. It read no
     * weights at all, which is not an error anybody sees -- it silently means "weigh every card
     * the same". Commons stopped being common and legendaries stopped being rare, because rarity
     * had stopped being consulted.
     *
     * <p>A flat table is returned untouched. The distinction is that a split table's values are
     * themselves tables; a rarity's value is always a number.
     */
    static JsonNode weightsForNodeKind(JsonNode container, String nodeType) {
        if (container == null || !container.isObject()) {
            return container;
        }
        String kind = nodeType == null ? "" : nodeType.toLowerCase(java.util.Locale.ROOT);
        List<String> preferred = new ArrayList<>();
        if (!kind.isBlank()) {
            preferred.add(kind);
        }
        if (!"boss".equals(kind)) {
            // Anything that is not the boss draws on the ordinary table, an elite included: the
            // elite's own generosity is its enchantment, not a different pool.
            preferred.add("normal");
        }
        preferred.add("default");

        for (String key : preferred) {
            JsonNode nested = container.path(key);
            if (nested.isObject() || nested.isArray()) {
                return nested;
            }
        }
        return container;
    }

    /**
     * Les tables à consulter pour cet acte, la plus précise d'abord.
     *
     * <p>N'importe quel niveau d'une table de récompenses — la section, la configuration d'un
     * type de nœud, le conteneur de poids lui-même — peut se décliner par acte sous
     * {@code byAct} (ou {@code acts}, ou {@code perAct}). L'acte retenu est le dernier écrit qui
     * ne dépasse pas l'acte courant : à l'Acte 3, une table qui ne connaît que les actes 1 et 2
     * répond avec celle de l'acte 2. Rien n'oblige donc à réécrire chaque acte.
     *
     * <p>Un acte antérieur à tout ce qui est écrit ne retient aucune déclinaison, et c'est la
     * table non déclinée qui répond — sans quoi un acte 1 hériterait des poids pensés pour la fin
     * de partie.
     *
     * <p>Les numéros s'écrivent comme le joueur les voit : {@code "1"} est l'Acte 1, comme
     * {@code startingAct} sur l'enemy pool.
     */
    static List<JsonNode> actScopes(JsonNode node, int act) {
        JsonNode self = node == null ? MissingNode.getInstance() : node;
        JsonNode scoped = tableForAct(self, act);
        return scoped == null ? List.of(self) : List.of(scoped, self);
    }

    /** @return la déclinaison d'acte la plus tardive qui s'applique, ou null quand il n'y en a pas */
    static JsonNode tableForAct(JsonNode node, int act) {
        if (node == null || !node.isObject()) {
            return null;
        }

        for (String key : new String[] { "byAct", "acts", "perAct" }) {
            JsonNode declined = node.path(key);
            JsonNode best = null;
            int bestAct = Integer.MIN_VALUE;

            if (declined.isObject()) {
                Iterator<String> names = declined.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    int authored = actNumber(name);
                    JsonNode value = declined.path(name);
                    if (authored == Integer.MIN_VALUE || authored > act || authored <= bestAct
                            || !value.isObject()) {
                        continue;
                    }
                    best = value;
                    bestAct = authored;
                }
            } else if (declined.isArray()) {
                for (JsonNode entry : declined) {
                    if (!entry.isObject()) {
                        continue;
                    }
                    int authored = entryAct(entry);
                    if (authored == Integer.MIN_VALUE || authored > act || authored <= bestAct) {
                        continue;
                    }
                    best = entry;
                    bestAct = authored;
                }
            }

            if (best != null) {
                return best;
            }
        }

        return null;
    }

    /**
     * Le numéro d'acte qu'une clé nomme.
     *
     * <p>Tolère « 2 », « act2 » et « Acte 2 » : la clé est écrite à la main, et trois façons de
     * dire le même acte valent mieux qu'une table silencieusement ignorée.
     */
    private static int actNumber(String key) {
        if (key == null) {
            return Integer.MIN_VALUE;
        }
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException invalid) {
            return Integer.MIN_VALUE;
        }
    }

    /** Le numéro d'acte porté par une entrée, quand la déclinaison est écrite comme une liste. */
    private static int entryAct(JsonNode entry) {
        for (String key : new String[] { "act", "minAct", "fromAct" }) {
            JsonNode value = entry.path(key);
            if (value.isIntegralNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                return actNumber(value.asText());
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean hasKnownCardRarityWeight(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return false;
        }
        return weights.keySet().stream().anyMatch(key -> switch (key) {
            case "common", "normal", "uncommon", "rare", "epic", "legendary", "mythic", "special", "default" -> true;
            default -> false;
        });
    }

    static Map<String, Integer> parseWeightMap(JsonNode container) {
        if (container == null || container.isNull() || container.isMissingNode()) {
            return Map.of();
        }

        Map<String, Integer> weights = new HashMap<>();

        if (container.isObject()) {
            Iterator<String> fieldNames = container.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode valueNode = container.path(fieldName);
                if (valueNode.isNumber()) {
                    weights.put(normalizeWeightKey(fieldName), Math.max(0, valueNode.asInt(0)));
                    continue;
                }

                if (valueNode.isObject()) {
                    int nestedWeight = valueNode.path("weight").asInt(valueNode.path("value").asInt(Integer.MIN_VALUE));
                    if (nestedWeight != Integer.MIN_VALUE) {
                        weights.put(normalizeWeightKey(fieldName), Math.max(0, nestedWeight));
                    }
                }
            }
        } else if (container.isArray()) {
            for (JsonNode item : container) {
                if (item == null || item.isNull()) {
                    continue;
                }

                String rarity = firstNonBlankText(item, "rarity", "id", "name", "tier");
                if (rarity.isBlank()) {
                    continue;
                }

                int weight = Integer.MIN_VALUE;
                if (item.path("weight").isNumber()) {
                    weight = item.path("weight").asInt();
                } else if (item.path("value").isNumber()) {
                    weight = item.path("value").asInt();
                } else if (item.path("chance").isNumber()) {
                    weight = item.path("chance").asInt();
                }

                if (weight != Integer.MIN_VALUE) {
                    weights.put(normalizeWeightKey(rarity), Math.max(0, weight));
                }
            }
        }

        return weights;
    }

    private int resolveConfiguredRarityWeight(Map<String, Integer> weights, String rarityToken) {
        if (weights == null || weights.isEmpty()) {
            return 1;
        }

        String normalized = normalizeWeightKey(rarityToken);
        if (weights.containsKey(normalized)) {
            return Math.max(0, weights.get(normalized));
        }

        if (weights.containsKey("default")) {
            return Math.max(0, weights.get("default"));
        }

        return 0;
    }

    private int resolveConfiguredRelicRarityWeight(Map<String, Integer> weights, JsonNode relic) {
        if (weights == null || weights.isEmpty()) {
            return 1;
        }

        if (isExplicitBossRelic(relic)) {
            if (weights.containsKey("boss")) {
                return Math.max(0, weights.get("boss"));
            }
            if (weights.containsKey("bossrelic")) {
                return Math.max(0, weights.get("bossrelic"));
            }
        }

        String rarityToken = relicRarityToken(relic);
        int directWeight = resolveConfiguredRarityWeight(weights, rarityToken);
        if (directWeight > 0) {
            return directWeight;
        }

        String normalized = normalizeWeightKey(rarityToken);
        if (normalized.isBlank()) {
            return directWeight;
        }

        if ("legendary".equals(normalized) || "mythic".equals(normalized) || "bossrelic".equals(normalized)) {
            if (weights.containsKey("boss")) {
                return Math.max(0, weights.get("boss"));
            }
            if (weights.containsKey("bossrelic")) {
                return Math.max(0, weights.get("bossrelic"));
            }
        }

        if ("boss".equals(normalized) && weights.containsKey("legendary")) {
            return Math.max(0, weights.get("legendary"));
        }

        return directWeight;
    }

    private boolean isExplicitBossRelic(JsonNode relic) {
        if (relic == null || relic.isNull()) {
            return false;
        }

        return relic.path("isBoss").asBoolean(false)
                || relic.path("bossRelic").asBoolean(false)
                || relic.path("isBossRelic").asBoolean(false);
    }

    private Map<String, WeightedCardChoice> highestRarityFallbackCards(Map<String, WeightedCardChoice> allEligibleById) {
        Map<String, WeightedCardChoice> filtered = new HashMap<>();
        if (allEligibleById == null || allEligibleById.isEmpty()) {
            return filtered;
        }

        int bestRank = Integer.MIN_VALUE;
        for (WeightedCardChoice entry : allEligibleById.values()) {
            if (entry == null || entry.card() == null) {
                continue;
            }

            int rank = cardRarityRank(cardRarityToken(entry.card()));
            if (rank > bestRank) {
                bestRank = rank;
            }
        }

        if (bestRank == Integer.MIN_VALUE) {
            filtered.putAll(allEligibleById);
            return filtered;
        }

        for (Map.Entry<String, WeightedCardChoice> mapEntry : allEligibleById.entrySet()) {
            WeightedCardChoice choice = mapEntry.getValue();
            if (choice == null || choice.card() == null) {
                continue;
            }

            if (cardRarityRank(cardRarityToken(choice.card())) == bestRank) {
                filtered.put(mapEntry.getKey(), new WeightedCardChoice(choice.card(), 1));
            }
        }

        if (filtered.isEmpty()) {
            filtered.putAll(allEligibleById);
        }

        return filtered;
    }

    private int cardRarityRank(String rarityToken) {
        String normalized = normalizeWeightKey(rarityToken);
        return switch (normalized) {
            case "legendary", "mythic" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            case "common", "normal" -> 1;
            default -> 0;
        };
    }

    private List<JsonNode> highestRarityRelicCandidates(List<JsonNode> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int bestRank = Integer.MIN_VALUE;
        for (JsonNode relic : candidates) {
            bestRank = Math.max(bestRank, relicRarityRank(relicRarityToken(relic)));
        }

        if (bestRank == Integer.MIN_VALUE) {
            return candidates;
        }

        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode relic : candidates) {
            if (relicRarityRank(relicRarityToken(relic)) == bestRank) {
                filtered.add(relic);
            }
        }

        return filtered.isEmpty() ? candidates : filtered;
    }

    private int relicRarityRank(String rarityToken) {
        String normalized = normalizeWeightKey(rarityToken);
        return switch (normalized) {
            case "boss", "bossrelic", "legendary", "mythic" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            case "common", "normal" -> 1;
            default -> 0;
        };
    }

    static String normalizeWeightKey(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim().toLowerCase();
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private ObjectNode rewardBase(String type) {
        ObjectNode reward = objectMapper.createObjectNode();
        reward.put("rewardId", "reward_" + UUID.randomUUID());
        reward.put("type", type);
        reward.put("claimed", false);
        return reward;
    }

    private record RetirementScoreBreakdown(
            long visitedNodeScore,
            long combatVictoryScore,
            long eliteVictoryScore,
            long eventVisitedScore,
            long actReachedScore,
            long relicOwnedScore,
            long deckCardScore,
            long goldOwnedScore,
            long remainingHpPercentScore,
            long totalScore
    ) {
    }

    private record WeightedJsonNode(
            JsonNode node,
            int weight
    ) {
    }

        private record WeightedCardChoice(
            JsonNode card,
            int weight
        ) {
        }

    private RetirementScoreBreakdown retirementScore(StsRun run, JsonNode scoreRules) {
        long visitedNodes = 0;
        long combatVictories = 0;
        long eliteVictories = 0;
        long eventsVisited = 0;
        for (JsonNode mapNode : run.getMapState().path("nodes")) {
            if (!mapNode.path("visited").asBoolean(false)) {
                continue;
            }
            visitedNodes++;
            String type = mapNode.path("type").asText();
            if (mapNode.path("completed").asBoolean(false)) {
                if ("Combat".equals(type)) {
                    combatVictories++;
                } else if ("Elite".equals(type)) {
                    eliteVictories++;
                }
            }
            if ("Event".equals(type)) {
                eventsVisited++;
            }
        }

        long maxHp = run.getPlayerState().path("maxHp").asLong(0);
        long currentHp = run.getPlayerState().path("currentHp").asLong(0);
        long hpPercent = maxHp <= 0 ? 0 : Math.round((double) currentHp / maxHp * 100.0);

        long visitedNodeScore = visitedNodes * scoreRules.path("visitedNode").asLong();
        long combatVictoryScore = combatVictories * scoreRules.path("combatVictory").asLong();
        long eliteVictoryScore = eliteVictories * scoreRules.path("eliteVictory").asLong();
        long eventVisitedScore = eventsVisited * scoreRules.path("eventVisited").asLong();
        // Les actes franchis, pas l'index courant : une run qui démarre à l'Acte 3 n'a franchi
        // aucun acte tant qu'elle y est encore.
        long actsCrossed = Math.max(0, Math.max(0, run.getAct()) - startingAct(run.getDataVersion()));
        long actReachedScore = actsCrossed * scoreRules.path("actReached").asLong();
        long relicOwnedScore = run.getRunInventory().path("relics").size() * scoreRules.path("relicOwned").asLong();
        long deckCardScore = run.getRunInventory().path("deck").size() * scoreRules.path("deckCard").asLong();
        long goldOwnedScore = Math.max(0, run.getRunInventory().path("gold").asLong()) * scoreRules.path("goldOwned").asLong();
        long remainingHpPercentScore = hpPercent * scoreRules.path("remainingHpPercent").asLong();
        long totalScore = visitedNodeScore
            + combatVictoryScore
            + eliteVictoryScore
            + eventVisitedScore
            + actReachedScore
            + relicOwnedScore
            + deckCardScore
            + goldOwnedScore
            + remainingHpPercentScore;

        return new RetirementScoreBreakdown(
            visitedNodeScore,
            combatVictoryScore,
            eliteVictoryScore,
            eventVisitedScore,
            actReachedScore,
            relicOwnedScore,
            deckCardScore,
            goldOwnedScore,
            remainingHpPercentScore,
            totalScore
        );
    }

    /** The total number of acts, authored once on the enemy pool rather than duplicated per table. */
    private int maxActs(String dataVersion) {
        return gameDataService.actCount(dataVersion);
    }

    /**
     * L'acte sur lequel une run démarre, en index interne.
     *
     * <p>Réglable depuis l'enemy pool, ce qui laisse une partie commencer directement à l'Acte 3
     * pour la tester. Tout ce qui mesure une progression compte à partir de là et non de zéro :
     * une run qui démarre au dernier acte n'a pas franchi les précédents.
     */
    private int startingAct(String dataVersion) {
        return gameDataService.startingAct(dataVersion);
    }

    /** Le numéro d'acte tel que les tables de configuration l'écrivent : 1 pour l'Acte 1. */
    private int authoredAct(StsRun run) {
        return Math.max(0, run.getAct() == null ? 0 : run.getAct()) + 1;
    }

    /**
     * Grants the run's final, one-shot token reward — on an explicit retire as well as on a game
     * over — instead of the per-node grants that used to run mid-run and paid out regardless of
     * how the run ended. Returns the locked user so callers don't have to look it up again.
     */
    /**
     * Les jetons qu'une run perdue rapporte quand même, et qui les inscrit au compte.
     *
     * <p>Réglé par {@code tokens.gameOverMultiplier} dans {@code gameplay/run_score_rewards.json} :
     * la part de ce qu'un retrait au même point aurait rendu. À 0, le défaut, une défaite ne
     * rapporte rien et rien n'est verrouillé ni écrit — le comportement d'avant ce réglage.
     *
     * <p>Le multiplicateur s'applique au nombre de jetons et non au score, pour que le plancher
     * {@code minimumReward} reste ce que vaut une run menée à son terme : une run perdue n'a pas
     * à l'atteindre. Le produit est tronqué, donc une part qui ne vaut pas un jeton n'en vaut
     * aucun.
     */
    private long grantGameOverTokens(StsRun run) {
        JsonNode rules = gameDataService.runScoreRewards(run.getDataVersion());
        double multiplier = rules == null
                ? 0
                : Math.max(0, rules.path("tokens").path("gameOverMultiplier").asDouble(0));
        if (multiplier <= 0) {
            return 0;
        }

        grantFinalTokens(run, multiplier);
        Long granted = run.getRetirementTokens();
        return granted == null ? 0 : granted;
    }

    private AppUser grantFinalTokens(StsRun run) {
        return grantFinalTokens(run, 1.0);
    }

    /**
     * @param tokenMultiplier la part du gain qui est réellement accordée : 1 pour une fin de run
     *                        ordinaire, moins pour une défaite
     */
    private AppUser grantFinalTokens(StsRun run, double tokenMultiplier) {
        JsonNode rules = gameDataService.runScoreRewards(run.getDataVersion());
        JsonNode scoreRules = rules.path("score");
        JsonNode tokenRules = rules.path("tokens");
        RetirementScoreBreakdown scoreBreakdown = retirementScore(run, scoreRules);
        long score = scoreBreakdown.totalScore();
        long fullTokens = retirementTokens(score, tokenRules, Math.max(0, run.getAct()),
                startingAct(run.getDataVersion()), maxActs(run.getDataVersion()));
        long tokens = tokenMultiplier >= 1.0
                ? fullTokens
                : (long) Math.floor(fullTokens * Math.max(0, tokenMultiplier));

        AppUser lockedUser = userRepository.findWithLockByKeycloakSub(run.getOwner().getKeycloakSub())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        lockedUser.setTokens(Math.addExact(lockedUser.getTokens(), tokens));
        long runEventPoints = lockedUser.creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource.GAME_RUN, tokens);
        currencyLedger.record(lockedUser, fr.insastronaute.webapi.economy.domain.CurrencySource.GAME_RUN, tokens, runEventPoints, run.getId());

        run.setRetirementScore(score);
        run.setRetirementTokens(tokens);
        return lockedUser;
    }

    private long retirementTokens(long score, JsonNode tokenRules, int currentAct, int startingAct, int maxActs) {
        long scorePerToken = tokenRules.path("scorePerToken").asLong();
        if (scorePerToken <= 0) {
            throw new BadRequestException("Configuration de score STS invalide.");
        }
        // Acts are 0-indexed while playing, so act 0 of a 3-act game is 1/3 of the way through.
        // Une run qui démarre plus loin ne mesure que les actes qu'elle pouvait jouer : partir à
        // l'Acte 3 et s'y arrêter, ce n'est pas avoir tout traversé.
        int floor = Math.min(Math.max(0, startingAct), Math.max(0, maxActs - 1));
        int playableActs = Math.max(1, maxActs - floor);
        double actProgress = Math.min(1.0, (Math.max(0, currentAct - floor) + 1) / (double) playableActs);
        double scaledScore = score * actProgress;
        long calculated = switch (tokenRules.path("rounding").asText()) {
            case "NEAREST" -> Math.round(scaledScore / scorePerToken);
            case "FLOOR" -> (long) Math.floor(scaledScore / scorePerToken);
            case "CEILING" -> (long) Math.ceil(scaledScore / scorePerToken);
            default -> throw new BadRequestException("Mode d'arrondi du score STS invalide.");
        };
        return Math.max(tokenRules.path("minimumReward").asLong(0), calculated);
    }

    private StsRetireRunResponse retirementResponse(
            StsRun run,
            long tokensGranted,
            long tokenBalance,
            JsonNode scoreRules,
            JsonNode tokenRules,
            List<String> pvpUnlockedCardNames
    ) {
        RetirementScoreBreakdown scoreBreakdown = retirementScore(run, scoreRules);
        long resolvedScore = run.getRetirementScore() != null ? run.getRetirementScore() : scoreBreakdown.totalScore();
        return new StsRetireRunResponse(
                true,
                run.getId(),
                run.getStatus(),
                resolvedScore,
                Math.max(0L, tokensGranted),
                tokenBalance,
                scoreBreakdown.visitedNodeScore(),
                scoreBreakdown.combatVictoryScore(),
                scoreBreakdown.eliteVictoryScore(),
                scoreBreakdown.eventVisitedScore(),
                scoreBreakdown.actReachedScore(),
                scoreBreakdown.relicOwnedScore(),
                scoreBreakdown.deckCardScore(),
                scoreBreakdown.goldOwnedScore(),
                scoreBreakdown.remainingHpPercentScore(),
                tokenRules.path("scorePerToken").asLong(0),
                tokenRules.path("rounding").asText(""),
                tokenRules.path("minimumReward").asLong(0),
                pvpUnlockedCardNames == null ? List.of() : pvpUnlockedCardNames
        );
    }

    private JsonNode eventOption(JsonNode activeEvent, String optionId) {
        for (JsonNode option : activeEvent.path("options")) {
            if (optionId.equals(option.path("optionId").asText())) {
                return option;
            }
        }
        throw new BadRequestException("Option d'event STS inconnue.");
    }

    private ObjectNode eventRunInventoryPatch() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("goldDelta", 0);
        patch.set("removedCardInstanceIds", objectMapper.createArrayNode());
        patch.set("addedCards", objectMapper.createArrayNode());
        patch.set("enchantedCards", objectMapper.createArrayNode());
        patch.set("addedRelics", objectMapper.createArrayNode());
        patch.set("updatedRelics", objectMapper.createArrayNode());
        return patch;
    }

    /// Applique les entrées d'une option.
    ///
    /// <p>Rend {@code true} quand l'événement doit rester ouvert — c'est le cas de
    /// ReplaceOptions, qui échange la liste d'options au lieu de clore le nœud.</p>
    private boolean applyEventEntries(
            StsRun run,
            JsonNode entries,
            StsChooseEventOptionRequest request,
            ObjectNode runInventoryPatch,
            ArrayNode createdRewards,
            String chosenOptionId
    ) {
        boolean eventContinues = false;
        for (JsonNode entry : entries) {
            String type = entry.path("type").asText();
            int value = Math.max(0, entry.path("value").asInt(0));
            switch (type) {
                case "Damage" -> damagePlayer(run, value);
                case "Heal" -> healPlayer(run, value);
                case "GoldReward" -> addGold(run, value, runInventoryPatch);
                case "MaxHpGain" -> gainMaxHp(run, value);
                case "MaxHpLoss" -> loseMaxHp(run, value);
                case "CardReward" -> {
                    // One reward per authored profile, and one ordinary reward when the entry
                    // authors none. Generating a plain reward here regardless is what made every
                    // event that asks for a particular pool -- another character's cards, a
                    // rarity, a cost band -- hand out an ordinary one instead.
                    List<EventCardRewardProfile> profiles = EventCardRewardProfile.allOf(entry);
                    if (profiles.isEmpty()) {
                        createdRewards.add(cardChoiceReward(run, "Event", false, null));
                    } else {
                        for (EventCardRewardProfile profile : profiles) {
                            createdRewards.add(cardChoiceReward(run, "Event", false, profile));
                        }
                    }
                }
                case "RelicReward" -> addEventRelicReward(run, entry, createdRewards);
                case "RemoveCard" -> removeSelectedCards(run, value, request.selectedCardInstanceIds(), runInventoryPatch);
                case "AddCard" -> addEventCards(run, entry, value, runInventoryPatch);
                case "UpgradeCard" -> enchantInventoryCards(
                        run, value, request.selectedCardInstanceIds(), 1 + random.nextInt(3), runInventoryPatch);
                // Une entrée sans effet reste une entrée valide : la refuser bloquerait
                // le nœud alors que l'option ne demandait rien.
                case "None" -> { }
                case "ReplaceOptions" -> eventContinues |= replaceEventOptions(run, entry, chosenOptionId);
                default -> throw new BadRequestException("Type d'entrée d'event STS inconnu.");
            }
        }
        return eventContinues;
    }

    private void damagePlayer(StsRun run, int amount) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        player.put("currentHp", Math.max(0, player.path("currentHp").asInt() - amount));
    }

    /**
     * Ouvre un feu de camp : les charges repartent au maximum, puis les reliques de
     * repos jouent, dans l'ordre où la run les porte.
     *
     * <p>Reprend STSRestState.InitialCharges du client, qui remet les charges au
     * maximum à chaque entrée plutôt que de les reporter.
     */
    private void openRestSite(StsRun run) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        int maxRestCharges = player.path("maxRestCharges").asInt(DEFAULT_MAX_REST_CHARGES);
        player.put("maxRestCharges", maxRestCharges);
        player.put("restCharges", Math.max(0, maxRestCharges));

        RelicHandler.RestOutcome outcome = stsAuthoritativeCombatService.relicEngine().processEnterRestSite(
                runRelicIds(run),
                new RelicHandler.RestContext(
                        player.path("currentHp").asInt(),
                        player.path("maxHp").asInt(),
                        player.path("restCharges").asInt()));
        player.put("currentHp", Math.min(player.path("maxHp").asInt(), Math.max(0, outcome.currentHp())));
        player.put("restCharges", Math.max(0, outcome.charges()));
    }

    private List<String> runRelicIds(StsRun run) {
        List<String> relicIds = new ArrayList<>();
        for (JsonNode relic : run.getRunInventory().path("relics")) {
            String relicId = relic.path("relicId").asText("");
            if (!relicId.isBlank()) {
                relicIds.add(relicId);
            }
        }
        return relicIds;
    }

    /**
     * Le feu de camp, côté serveur.
     *
     * <p>Le soin et l'enchantement se décidaient dans le client, qui annonçait
     * ensuite ses points de vie à completeNode. Ils se décident ici.
     */
    @Transactional
    public StsRestActionResponse rest(AppUser owner, UUID runId, int nodeId, StsRestActionRequest request) {
        StsRun run = ownedRunWithLock(owner, runId);
        requireActive(run);
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new BadRequestException("Action de feu de camp STS requise.");
        }
        if (run.getEnteredNodeId() == null || run.getEnteredNodeId() != nodeId) {
            throw new ConflictException("Ce feu de camp STS n'est pas en cours.");
        }
        ObjectNode node = node(run.getMapState(), nodeId);
        if (!REST_NODE_TYPE.equals(node.path("type").asText())) {
            throw new ConflictException("Le node STS courant n'est pas un feu de camp.");
        }

        ObjectNode player = (ObjectNode) run.getPlayerState();
        // Une run déjà à l'intérieur d'un feu de camp au moment du déploiement n'a
        // pas de charges en base : sans ce rattrapage l'endpoint les verrait à zéro
        // et refuserait tout, laissant le joueur devant un feu de camp inerte.
        // C'est le seul endroit où « absent » et « épuisé » ne veulent pas dire la
        // même chose.
        if (!player.has("restCharges")) {
            openRestSite(run);
        }

        ObjectNode runInventoryPatch = eventRunInventoryPatch();
        String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
        switch (action) {
            case "HEAL" -> restHeal(run, player);
            case "ENCHANT" -> restEnchant(run, player, request, runInventoryPatch);
            default -> throw new BadRequestException("Action de feu de camp STS inconnue.");
        }

        return new StsRestActionResponse(
                true,
                action,
                apiJson(run.getPlayerState()),
                apiJson(runInventoryPatch),
                player.path("restCharges").asInt(),
                player.path("maxRestCharges").asInt(DEFAULT_MAX_REST_CHARGES));
    }

    /**
     * Soigne un douzième des points de vie maximum par charge, et s'arrête dès que
     * le joueur est au maximum — les charges qui restent servent à enchanter.
     *
     * <p>C'est la boucle de RestManager.OnRest, portée telle quelle.
     */
    private void restHeal(StsRun run, ObjectNode player) {
        int maxHp = player.path("maxHp").asInt();
        int perCharge = (int) Math.floor(maxHp / 12.0);
        int charges = player.path("restCharges").asInt();
        int currentHp = player.path("currentHp").asInt();
        while (charges > 0 && currentHp < maxHp) {
            currentHp = Math.min(maxHp, currentHp + perCharge);
            charges--;
        }
        player.put("currentHp", currentHp);
        player.put("restCharges", charges);
    }

    /**
     * Dépense des charges pour enchanter une carte du deck.
     *
     * <p>Le niveau se tire dans [charges, 2×charges), comme UnityEngine.Random.Range
     * côté client — mais avec l'aléa du serveur, qui est le point de tout ceci.
     */
    private void restEnchant(
            StsRun run,
            ObjectNode player,
            StsRestActionRequest request,
            ObjectNode runInventoryPatch
    ) {
        int charges = request.charges() == null ? 0 : request.charges();
        if (charges < 1) {
            throw new BadRequestException("Nombre de charges de feu de camp STS invalide.");
        }
        int available = player.path("restCharges").asInt();
        if (charges > available) {
            throw new BadRequestException("Charges de feu de camp STS insuffisantes.");
        }
        if (request.cardInstanceId() == null || request.cardInstanceId().isBlank()) {
            throw new BadRequestException("Carte à enchanter requise.");
        }

        int level = charges + random.nextInt(charges);
        enchantInventoryCards(run, 1, List.of(request.cardInstanceId()), level, runInventoryPatch);
        player.put("restCharges", available - charges);
    }

    private void healPlayer(StsRun run, int amount) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        int maxHp = player.path("maxHp").asInt();
        player.put("currentHp", Math.min(maxHp, player.path("currentHp").asInt() + amount));
    }

    private void addGold(StsRun run, int amount, ObjectNode runInventoryPatch) {
        ObjectNode inventory = (ObjectNode) run.getRunInventory();
        inventory.put("gold", inventory.path("gold").asInt() + amount);
        runInventoryPatch.put("goldDelta", runInventoryPatch.path("goldDelta").asInt() + amount);
    }

    private void gainMaxHp(StsRun run, int amount) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        player.put("maxHp", player.path("maxHp").asInt() + amount);
        player.put("currentHp", player.path("currentHp").asInt() + amount);
    }

    private void loseMaxHp(StsRun run, int amount) {
        ObjectNode player = (ObjectNode) run.getPlayerState();
        int maxHp = Math.max(1, player.path("maxHp").asInt() - amount);
        player.put("maxHp", maxHp);
        player.put("currentHp", Math.min(player.path("currentHp").asInt(), maxHp));
    }

    private void addEventRelicReward(StsRun run, JsonNode entry, ArrayNode createdRewards) {
        String relicId = entry.path("id").asText("");
        if (!relicId.isBlank()) {
            ObjectNode reward = rewardBase("Relic");
            reward.put("relicId", relicId);
            createdRewards.add(reward);
            return;
        }
        ObjectNode reward = relicReward(run, "Event");
        if (reward != null) {
            createdRewards.add(reward);
        }
    }

    /// Ajoute au deck les cartes qu'une option d'événement accorde.
    ///
    /// <p>Le client tirait ces cartes lui-même ; elles disparaissaient au premier
    /// resynchro puisque l'état de run ne les avait jamais vues. Une entrée sans
    /// identifiant vaut « une carte au hasard parmi celles que le personnage peut
    /// obtenir », comme côté client.</p>
    private void addEventCards(StsRun run, JsonNode entry, int count, ObjectNode runInventoryPatch) {
        if (count <= 0) {
            return;
        }
        String dataVersion = run.getDataVersion();
        String requestedCardId = entry.path("id").asText("");

        ArrayNode deck = (ArrayNode) run.getRunInventory().path("deck");
        ArrayNode addedPatch = (ArrayNode) runInventoryPatch.get("addedCards");

        for (int i = 0; i < count; i++) {
            JsonNode card = requestedCardId.isBlank()
                    ? randomEligibleCard(run)
                    : cardById(dataVersion, requestedCardId);
            if (card == null) {
                throw new BadRequestException("Carte STS introuvable pour cette entrée d'event.");
            }
            int before = deck.size();
            addCardInstances(deck, card, 1);
            for (int index = before; index < deck.size(); index++) {
                addedPatch.add(deck.get(index));
            }
        }
    }

    /// Échange des options de l'événement en cours au lieu de clore le nœud.
    ///
    /// <p>C'est une option qui en ouvre d'autres. Le client le faisait seul, donc le
    /// serveur voyait un nœud terminé là où le joueur avait encore un choix à faire.</p>
    ///
    /// <p>Sans {@code targetIds}, c'est l'option choisie qui cède la place ; sinon ce
    /// sont celles qu'ils désignent. Les remplaçantes prennent la position de la
    /// première remplacée, pour que l'ordre affiché reste celui qu'attend l'auteur de
    /// l'événement.</p>
    private boolean replaceEventOptions(StsRun run, JsonNode entry, String chosenOptionId) {
        JsonNode replacements = entry.path("replacementOptions");
        if (!replacements.isArray()) {
            throw new BadRequestException("Entrée ReplaceOptions sans options de remplacement.");
        }

        Set<String> targets = new HashSet<>();
        for (JsonNode target : entry.path("targetIds")) {
            String id = target.asText("");
            if (!id.isBlank()) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            targets.add(chosenOptionId);
        }

        ArrayNode options = (ArrayNode) run.getActiveEvent().path("options");
        ArrayNode rebuilt = objectMapper.createArrayNode();
        boolean inserted = false;
        boolean matched = false;

        for (JsonNode option : options) {
            if (!targets.contains(option.path("optionId").asText())) {
                rebuilt.add(option);
                continue;
            }
            matched = true;
            if (!inserted) {
                int replacementIndex = 0;
                for (JsonNode replacement : replacements) {
                    // L'identifiant engendré descend de celui de l'option choisie : une
                    // chaîne de remplacements peut se dérouler sur plusieurs tours sans
                    // qu'un palier retombe sur le nom d'un autre.
                    rebuilt.add(activeEventOption(
                            replacement, chosenOptionId + ".r" + replacementIndex));
                    replacementIndex++;
                }
                inserted = true;
            }
        }

        if (!matched) {
            throw new BadRequestException("Aucune option d'event STS ne correspond à ReplaceOptions.");
        }

        ((ObjectNode) run.getActiveEvent()).set("options", rebuilt);
        return true;
    }

    /// Enchante une carte de l'inventaire, côté serveur.
    ///
    /// <p>C'est le geste que le client faisait seul : tirer un enchantement et le poser
    /// sur une carte choisie. Deux endroits en ont besoin — l'entrée UpgradeCard d'un
    /// événement, et l'action ENCHANT du feu de camp — avec pour seule différence le
    /// nombre de charges dépensées.</p>
    ///
    /// <p>Le tirage passe par EnchantmentRoller, dont la table est déjà l'exacte
    /// transposition de celle d'Unity, poids pour poids : basculer ne change donc pas
    /// l'équilibrage.</p>
    private void enchantInventoryCards(
            StsRun run,
            int requiredCount,
            List<String> selectedCardInstanceIds,
            int charges,
            ObjectNode runInventoryPatch
    ) {
        if (selectedCardInstanceIds == null || selectedCardInstanceIds.size() != requiredCount) {
            throw new BadRequestException("La sélection de cartes STS est invalide.");
        }

        String dataVersion = run.getDataVersion();
        JsonNode sourceCatalog = gameDataService.cards(dataVersion);
        ArrayNode deck = (ArrayNode) run.getRunInventory().path("deck");
        ArrayNode enchantedPatch = (ArrayNode) runInventoryPatch.get("enchantedCards");

        for (String instanceId : selectedCardInstanceIds) {
            ObjectNode card = null;
            for (JsonNode candidate : deck) {
                if (instanceId.equals(candidate.path("instanceId").asText())) {
                    card = (ObjectNode) candidate;
                    break;
                }
            }
            if (card == null) {
                throw new BadRequestException("Une carte STS sélectionnée est introuvable dans le deck.");
            }

            String cardId = card.path("cardId").asText();
            CardCatalog catalog = cardCatalogFactory.create(
                    sourceCatalog, dataVersion, List.of(cardId), run.getSelectedCharacter());
            CardDefinition definition = catalog.cards().get(cardId);
            if (definition == null) {
                throw new BadRequestException("Définition de carte STS introuvable pour l'enchantement.");
            }

            List<EnchantmentInstance> existing = new ArrayList<>();
            for (JsonNode enchantment : card.path("enchantments")) {
                existing.add(new EnchantmentInstance(
                        enchantment.path("enchantmentId").asText(),
                        enchantment.path("level").asInt()));
            }

            EnchantmentRoller.Roll roll = EnchantmentRoller.roll(
                    new CardInstance(instanceId, cardId, existing),
                    definition,
                    Math.max(1, charges),
                    new RandomState(random.nextLong()));

            ArrayNode rewritten = objectMapper.createArrayNode();
            for (EnchantmentInstance enchantment : roll.instance().enchantments()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("enchantmentId", enchantment.enchantmentId());
                node.put("level", enchantment.level());
                rewritten.add(node);
            }
            card.set("enchantments", rewritten);
            enchantedPatch.add(card);
        }
    }

    /**
     * Looks up a card by its id, or by its display name ({@code cardName}) when authors write
     * events referencing cards the way a player sees them rather than by internal id.
     */
    private JsonNode cardById(String dataVersion, String cardId) {
        JsonNode byName = null;
        for (JsonNode card : gameDataService.cards(dataVersion).path("cards")) {
            if (cardId.equals(card.path("id").asText())) {
                return card;
            }
            if (byName == null && cardId.equalsIgnoreCase(card.path("cardName").asText("").trim())) {
                byName = card;
            }
        }
        return byName;
    }

    private JsonNode randomEligibleCard(StsRun run) {
        String characterId = run.getSelectedCharacter();
        boolean secretsUnlocked = secretCardsUnlocked(run);
        List<JsonNode> eligible = new ArrayList<>();
        for (JsonNode card : gameDataService.cards(run.getDataVersion()).path("cards")) {
            if ((secretsUnlocked || !isSecretCard(card)) && isEligibleRewardCard(card, characterId)) {
                eligible.add(card);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    private void removeSelectedCards(StsRun run, int requiredCount, List<String> selectedCardInstanceIds, ObjectNode runInventoryPatch) {
        if (selectedCardInstanceIds == null || selectedCardInstanceIds.size() != requiredCount) {
            throw new BadRequestException("La sélection de cartes STS est invalide.");
        }
        Set<String> selectedIds = new HashSet<>(selectedCardInstanceIds);
        if (selectedIds.size() != selectedCardInstanceIds.size()) {
            throw new BadRequestException("La sélection de cartes STS contient des doublons.");
        }
        ArrayNode deck = (ArrayNode) run.getRunInventory().path("deck");
        ArrayNode remainingDeck = objectMapper.createArrayNode();
        Set<String> removedIds = new HashSet<>();
        for (JsonNode card : deck) {
            String instanceId = card.path("instanceId").asText();
            if (selectedIds.contains(instanceId)) {
                removedIds.add(instanceId);
            } else {
                remainingDeck.add(card);
            }
        }
        if (!removedIds.equals(selectedIds)) {
            throw new BadRequestException("Une carte STS sélectionnée est introuvable dans le deck.");
        }
        ((ObjectNode) run.getRunInventory()).set("deck", remainingDeck);
        ArrayNode removedPatch = (ArrayNode) runInventoryPatch.get("removedCardInstanceIds");
        selectedCardInstanceIds.forEach(removedPatch::add);
    }

    private void claimCardChoice(
            StsRun run, ObjectNode inventory, ObjectNode reward, StsClaimRewardRequest request) {
        if (request == null || request.selectedCardId() == null || request.selectedCardId().isBlank()) {
            throw new BadRequestException("La carte choisie est requise.");
        }
        JsonNode selectedChoice = null;
        for (JsonNode choice : reward.path("choices")) {
            if (request.selectedCardId().equals(choice.path("cardId").asText())) {
                selectedChoice = choice;
                break;
            }
        }
        if (selectedChoice == null) {
            throw new BadRequestException("La carte choisie n'appartient pas à cette reward.");
        }
        // Le filtre d'affichage ne suffit pas : une ancienne réponse, ou un client qui ne l'a
        // jamais reçue, peut encore désigner la carte. C'est la prise qui doit la refuser.
        if (!secretCardsUnlocked(run) && secretCardIds(run.getDataVersion()).contains(request.selectedCardId())) {
            throw new BadRequestException("Cette carte n'est pas encore disponible.");
        }
        ObjectNode cardInstance = objectMapper.createObjectNode();
        cardInstance.put("instanceId", "cardinst_" + UUID.randomUUID());
        cardInstance.put("cardId", request.selectedCardId());
        cardInstance.put("targetingMode", authoredTargeting(run, request.selectedCardId()));
        cardInstance.set("enchantments", selectedChoice.path("enchantments").deepCopy());
        ((ArrayNode) inventory.get("deck")).add(cardInstance);
    }

    /**
     * La visée que la carte s'est vu donner par ses auteurs.
     *
     * <p>Une carte gagnée en récompense entrait au deck visant systématiquement un ennemi, alors
     * que le deck de départ, lui, lit la visée du catalogue. Un soin ou une armure obtenus après
     * un combat se retrouvaient donc à demander une cible ennemie pour se lancer sur soi.
     *
     * <p>« Enemy » reste le défaut, comme pour le deck de départ : c'est ce que vise la grande
     * majorité des cartes, et une carte sans visée déclarée n'a pas à faire échouer un gain.
     */
    private String authoredTargeting(StsRun run, String cardId) {
        for (JsonNode card : gameDataService.cards(run.getDataVersion()).path("cards")) {
            if (cardId.equals(card.path("id").asText())) {
                return card.path("targetingMode").asText("Enemy");
            }
        }
        return "Enemy";
    }

    private void claimRelic(StsRun run, ObjectNode inventory, ObjectNode reward) {
        String relicId = reward.path("relicId").asText();
        ObjectNode relic = objectMapper.createObjectNode();
        relic.put("instanceId", "relicinst_" + UUID.randomUUID());
        relic.put("relicId", relicId);
        relic.put("stage", 0);
        ((ArrayNode) inventory.get("relics")).add(relic);

        // These are run-level acquisition costs in the Unity relics. Combat handlers
        // cannot apply them because they operate on temporary combatant state.
        switch (relicId) {
            case "BloodPactRelic" -> loseMaxHp(run, 10);
            case "CrownOfAshesRelic" -> loseMaxHp(run, 12);
            case "GlassCannonRelic" -> loseMaxHp(run, 20);
            case "SovereignHaloRelic" -> loseMaxHp(run, 5);
            default -> {
            }
        }
    }

    private void claimBaseRelicUpgrade(ObjectNode inventory, ObjectNode reward) {
        for (JsonNode relicNode : inventory.path("relics")) {
            if (relicNode instanceof ObjectNode relic && reward.path("relicInstanceId").asText().equals(relic.path("instanceId").asText())) {
                relic.put("stage", reward.path("stage").asInt());
                return;
            }
        }
        throw new BadRequestException("Relique STS à améliorer introuvable.");
    }

    private ObjectNode reward(ArrayNode rewards, String rewardId) {
        if (rewardId == null || rewardId.isBlank()) {
            throw new BadRequestException("Reward STS requise.");
        }
        for (JsonNode reward : rewards) {
            if (reward.path("rewardId").asText().equals(rewardId) && reward instanceof ObjectNode objectNode) {
                return objectNode;
            }
        }
        throw new NotFoundException("Reward STS introuvable.");
    }

    private void replacePendingRewards(StsRun run, ArrayNode createdRewards) {
        ArrayNode pendingRewards = objectMapper.createArrayNode();
        if (createdRewards != null) {
            pendingRewards.addAll(createdRewards);
        }
        run.setPendingRewards(pendingRewards);
    }

    /**
     * Les récompenses encore à prendre, telles que le joueur a le droit de les voir.
     *
     * <p>Une récompense est tirée une fois puis conservée avec la run. Une carte secrète qui y
     * est entrée avant que la génération ne les écarte — tirée par un serveur qui ne connaissait
     * pas encore le pool secret, par exemple — y reste donc, et serait montrée puis prise tant
     * qu'on se contente de filtrer au tirage. Pour un compte qui n'a pas ouvert le pool, ces
     * choix sont retirés de ce qui part vers le client ; la récompense enregistrée, elle, n'est
     * pas réécrite, et redevient entière le jour où le compte ouvre le pool.
     */
    private ArrayNode unclaimedRewards(StsRun run) {
        ArrayNode rewards = (ArrayNode) run.getPendingRewards();
        ArrayNode unclaimed = objectMapper.createArrayNode();
        Set<String> hiddenSecretCardIds = null;
        for (JsonNode reward : rewards) {
            if (reward.path("claimed").asBoolean(false)) {
                continue;
            }
            if (!"CardChoice".equals(reward.path("type").asText())) {
                unclaimed.add(reward);
                continue;
            }
            // Le catalogue n'est consulté que s'il y a une carte à juger, et une seule fois.
            if (hiddenSecretCardIds == null) {
                hiddenSecretCardIds = secretCardsUnlocked(run) ? Set.of() : secretCardIds(run.getDataVersion());
            }
            unclaimed.add(withoutSecretChoices(reward, hiddenSecretCardIds));
        }
        return unclaimed;
    }

    /** Les identifiants des cartes que ce catalogue marque {@code Secret}. */
    private Set<String> secretCardIds(String dataVersion) {
        JsonNode catalog = gameDataService.cards(dataVersion);
        if (catalog == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode card : catalog.path("cards")) {
            String id = card.path("id").asText("");
            if (!id.isBlank() && isSecretCard(card)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Une copie de la récompense sans ses choix secrets, ou la récompense elle-même s'il n'y en a pas. */
    private JsonNode withoutSecretChoices(JsonNode reward, Set<String> secretCardIds) {
        if (secretCardIds.isEmpty()) {
            return reward;
        }
        boolean anySecret = false;
        for (JsonNode choice : reward.path("choices")) {
            if (secretCardIds.contains(choice.path("cardId").asText(""))) {
                anySecret = true;
                break;
            }
        }
        if (!anySecret) {
            return reward;
        }
        ObjectNode copy = ((ObjectNode) reward).deepCopy();
        ArrayNode kept = copy.putArray("choices");
        for (JsonNode choice : reward.path("choices")) {
            if (!secretCardIds.contains(choice.path("cardId").asText(""))) {
                kept.add(choice.deepCopy());
            }
        }
        return copy;
    }

    private StsCompleteNodeResponse completionResponse(StsRun run, JsonNode runInventoryPatch, JsonNode accountInventoryPatch) {
        return new StsCompleteNodeResponse(
                true,
                run.getId(),
                run.getCurrentNodeId(),
                apiJson(run.getPlayerState()),
                apiJson(runInventoryPatch),
                apiJson(accountInventoryPatch),
                apiJson(unclaimedRewards(run)),
                apiJson(mapPatch(run))
        );
    }

    private ObjectNode mapPatch(StsRun run) {
        ObjectNode mapPatch = objectMapper.createObjectNode();
        ArrayNode visited = mapPatch.putArray("visitedNodeIds");
        ArrayNode completed = mapPatch.putArray("completedNodeIds");
        for (JsonNode node : run.getMapState().path("nodes")) {
            if (node.path("visited").asBoolean(false)) {
                visited.add(node.path("id").asInt());
            }
            if (node.path("completed").asBoolean(false)) {
                completed.add(node.path("id").asInt());
            }
        }
        mapPatch.put("currentNodeId", run.getCurrentNodeId());
        if (run.getEnteredNodeId() == null) {
            mapPatch.putNull("enteredNodeId");
        } else {
            mapPatch.put("enteredNodeId", run.getEnteredNodeId());
        }
        return mapPatch;
    }

    private JsonNode weightedEntry(JsonNode entries) {
        double total = 0;
        for (JsonNode entry : entries) {
            total += Math.max(0d, entry.path("weight").asDouble(0d));
        }
        if (total <= 0d) {
            return entries.get(0);
        }

        double roll = random.nextDouble() * total;
        double cursor = 0d;
        for (JsonNode entry : entries) {
            cursor += Math.max(0d, entry.path("weight").asDouble(0d));
            if (roll < cursor) {
                return entry;
            }
        }
        return entries.get(0);
    }

    private String weightedKey(JsonNode weights) {
        int total = 0;
        Iterator<String> fields = weights.fieldNames();
        while (fields.hasNext()) {
            total += Math.max(0, weights.path(fields.next()).asInt(0));
        }
        int roll = randomBetween(0, Math.max(total, 1));
        int cursor = 0;
        Iterator<String> weightedFields = weights.fieldNames();
        while (weightedFields.hasNext()) {
            String field = weightedFields.next();
            cursor += Math.max(0, weights.path(field).asInt(0));
            if (roll < cursor) {
                return field;
            }
        }
        return "Combat";
    }

    private boolean containsTextIgnoreCase(JsonNode token, String value) {
        if (token == null || token.isNull() || token.isMissingNode()) {
            return false;
        }

        if (token.isArray()) {
            for (JsonNode item : token) {
                if (value.equalsIgnoreCase(item.asText())) {
                    return true;
                }
            }
            return false;
        }

        String text = token.asText("").trim();
        return !text.isBlank() && text.toLowerCase().contains(value.toLowerCase());
    }

    private JsonNode existingOrChooseEvent(StsRun run, int nodeId) {
        JsonNode activeEvent = run.getActiveEvent();
        if (activeEvent != null && !activeEvent.isNull()) {
            return activeEvent;
        }
        return chooseEvent(run.getDataVersion(), nodeId);
    }

    private JsonNode chooseEvent(String dataVersion, int nodeId) {
        JsonNode events = gameDataService.events(dataVersion).path("events");
        if (!events.isArray() || events.isEmpty()) {
            return null;
        }
        int eventIndex = random.nextInt(events.size());
        JsonNode event = events.get(eventIndex);
        if (event == null || event.isNull()) {
            return null;
        }
        return activeEvent(event, eventIndex, nodeId);
    }

    private ObjectNode activeEvent(JsonNode sourceEvent, int eventIndex, int nodeId) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventInstanceId", "evtinst_" + UUID.randomUUID());
        event.put("nodeId", nodeId);
        String eventId = sourceId(sourceEvent, "eventId", "event_" + eventIndex);
        event.put("eventId", eventId);
        event.put("id", eventId);
        event.put("title", sourceEvent.path("title").asText(""));
        event.put("description", sourceEvent.path("description").asText(""));
        event.put("imageName", sourceEvent.path("imageName").asText(""));
        event.put("status", "Active");
        event.put("startedAt", fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().toString());
        ArrayNode options = event.putArray("options");
        int optionIndex = 0;
        for (JsonNode sourceOption : sourceEvent.path("options")) {
            options.add(activeEventOption(sourceOption, "option_" + optionIndex));
            optionIndex++;
        }
        return event;
    }

    /**
     * Met une option du catalogue dans la forme que l'événement en cours donne aux siennes.
     *
     * <p>Le catalogue exporté par Unity ne nomme pas ses options : le champ {@code id} y est
     * vide sur toutes, et c'est {@code generatedId} qui les distingue. Une option sans
     * {@code optionId} est une option que {@link #eventOption(JsonNode, String)} ne peut pas
     * retrouver et que le client saute à l'affichage, donc toute option qui entre dans
     * {@code options} passe par ici — celles de départ comme les remplaçantes.</p>
     */
    private ObjectNode activeEventOption(JsonNode sourceOption, String generatedId) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("optionId", sourceId(sourceOption, "optionId", generatedId));
        option.put("text", sourceOption.path("text").asText(""));
        option.put("iconName", sourceOption.path("iconName").asText(""));
        option.put("completionMessage", sourceOption.path("completionMessage").asText(""));
        option.set("entries", sourceOption.path("entries").deepCopy());
        option.set("selectionRequirements", eventSelectionRequirements(sourceOption.path("entries")));
        return option;
    }

    private String sourceId(JsonNode source, String primaryField, String generatedId) {
        String primary = source.path(primaryField).asText("");
        if (!primary.isBlank()) {
            return primary;
        }
        String fallback = source.path("id").asText("");
        return fallback.isBlank() ? generatedId : fallback;
    }

    private ArrayNode eventSelectionRequirements(JsonNode entries) {
        ArrayNode requirements = objectMapper.createArrayNode();
        for (JsonNode entry : entries) {
            if (!"RemoveCard".equals(entry.path("type").asText())) {
                continue;
            }
            ObjectNode requirement = objectMapper.createObjectNode();
            requirement.put("selectionType", "DeckCardPick");
            requirement.put("count", Math.max(0, entry.path("value").asInt(0)));
            requirement.putNull("allowedCardInstanceIds");
            requirement.put("description", "Choose cards to remove from your run deck.");
            requirements.add(requirement);
        }
        return requirements;
    }

    private String eventId(JsonNode event) {
        if (event == null || event.isNull()) {
            return null;
        }
        String id = event.path("id").asText(null);
        if (id == null || id.isBlank()) {
            id = event.path("eventId").asText(null);
        }
        return id == null || id.isBlank() ? null : id;
    }

    private int randomBetween(int minInclusive, int maxExclusive) {
        if (maxExclusive <= minInclusive) {
            return minInclusive;
        }
        return random.nextInt(maxExclusive - minInclusive) + minInclusive;
    }
}
