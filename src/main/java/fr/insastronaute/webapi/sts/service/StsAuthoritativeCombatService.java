package fr.insastronaute.webapi.sts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.api.CombatCommandMetrics;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.engine.EffectiveCard;
import fr.insastronaute.webapi.sts.combat.catalog.EnemyMoveParser;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.ai.EnemyPatternReader;
import fr.insastronaute.webapi.sts.combat.engine.CombatOrchestrator;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandRequest;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.dto.StsCombatStateResponse;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.insastronaute.webapi.sts.combat.model.CombatRuntimeContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StsAuthoritativeCombatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StsAuthoritativeCombatService.class);

    private static final String PLAYER_COMBATANT_ID = "player";
    private static final String PLAYER_TEAM_ID = "player";
    private static final String ENEMY_TEAM_ID = "enemy";
    private static final int DEFAULT_BASE_ENERGY = 3;
    // Must match TurnSystem.baseDelay in Unity (25), otherwise percentage-based turn-delay
    // effects (AdvanceTurn/DelayTurn/CutInTurn) resolve 4x stronger server-side than the
    // client's local preview predicts.
    private static final int DEFAULT_TURN_DELAY_TICKS = 25;

    private final EnemyMoveParser enemyMoveParser = new EnemyMoveParser();
    /**
     * The enemy definitions already looked at, keyed by data version, enemy id and combatant slot.
     *
     * <p>A pattern is rebuilt for every command a player sends, not once per combat, so an enemy
     * whose moves cannot be read would otherwise repeat the same lines dozens of times per fight
     * and again for every player who meets it. The defect belongs to the game data, not to the
     * run, so it is said once per process and stays true until the data is fixed and redeployed.
     */
    private final Set<String> diagnosedEnemies = ConcurrentHashMap.newKeySet();
    private final CardCatalogFactory cardCatalogFactory = new CardCatalogFactory();

    private final StsGameDataService gameDataService;
    private final ObjectMapper objectMapper;
    private final RelicEngine relicEngine;
    private final fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine enchantmentEngine;
    private final CombatCommandMetrics commandMetrics;

    private final Cache<UUID, CombatRuntimeContext> runtimeContextCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private CombatRuntimeContext buildRuntimeContext(StsRun run, UUID combatId) {
        CardCatalog catalog = catalogForRun(run);
        CombatEngine engine = new CombatEngine(catalog, relicEngine, enchantmentEngine);
        CombatOrchestrator orchestrator = new CombatOrchestrator(engine, policyForRun(run, catalog));
        return new CombatRuntimeContext(combatId, catalog, engine, orchestrator);
    }

    @Autowired
    public StsAuthoritativeCombatService(
            StsGameDataService gameDataService,
            ObjectMapper objectMapper,
            List<RelicHandler> relicHandlers,
            fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine enchantmentEngine,
            CombatCommandMetrics commandMetrics
    ) {
        this.gameDataService = gameDataService;
        this.objectMapper = objectMapper;
        this.relicEngine = new RelicEngine(relicHandlers);
        this.enchantmentEngine = enchantmentEngine;
        this.commandMetrics = commandMetrics;
    }

    /**
     * Le moteur de reliques, pour le feu de camp.
     *
     * <p>RelicEngine n'est pas un bean : il est assemblé ici depuis les handlers que
     * Spring fournit. Le feu de camp en a besoin sans avoir de combat, et le lui
     * prêter coûte moins que de le construire une seconde fois — deux instances
     * finiraient par diverger sans que rien ne le signale.
     */
    public RelicEngine relicEngine() {
        return relicEngine;
    }

    public JsonNode createCombatState(StsRun run) {
        Objects.requireNonNull(run, "run");
        if (run.getActiveEncounter() == null || run.getActiveEncounter().isNull()) {
            throw new IllegalArgumentException("active encounter is required");
        }

        CardCatalog catalog = catalogForRun(run);
        CombatSetup setup = buildSetup(run, catalog);
        CombatCreated created = new CombatFactory(catalog, relicEngine).create(setup);

        CombatEngine engine = new CombatEngine(catalog, relicEngine, enchantmentEngine);
        CombatOrchestrator orchestrator = new CombatOrchestrator(engine, policyForRun(run, catalog));

        CombatRuntimeContext context = new CombatRuntimeContext(
                setup.combatId(), catalog, engine, orchestrator);
        runtimeContextCache.put(setup.combatId(), context);

        CombatState stateWithIntents = orchestrator.updateIntents(created.state());
        return objectMapper.valueToTree(stateWithIntents);
    }

    public StsCombatStateResponse combatState(StsRun run) {
        Objects.requireNonNull(run, "run");
        if (run.getActiveCombat() == null || run.getActiveCombat().isNull()) {
            return new StsCombatStateResponse(false, run.getId(), null);
        }
        return new StsCombatStateResponse(true, run.getId(), objectMapper.convertValue(run.getActiveCombat(), Object.class));
    }

    public StsCombatCommandResponse submitCommand(StsRun run, StsCombatCommandRequest request) {
        Objects.requireNonNull(run, "run");
        if (run.getActiveCombat() == null || run.getActiveCombat().isNull()) {
            throw new ConflictException("Aucun combat STS authoritative actif.");
        }
        if (request == null) {
            throw new BadRequestException("La commande de combat STS est requise.");
        }

        try {
            CombatState state = commandMetrics.record("deserialize", () ->
                    requireCombatState(run.getActiveCombat()));
            UUID combatId = state.combatId();
            CombatRuntimeContext context = commandMetrics.record("catalog", () ->
                    runtimeContextCache.get(combatId, id -> buildRuntimeContext(run, id)));
            CardCatalog catalog = context.catalog();
            CombatEngine engine = context.engine();
            CombatOrchestrator orchestrator = context.orchestrator();
            CombatCommand command = commandMetrics.record("decode", () ->
                    commandFromRequest(state, request));

            CombatResult result = commandMetrics.record("player_engine", () ->
                    engine.handle(state, command));
            if (!result.accepted()) {
                return commandMetrics.record("serialize", () -> new StsCombatCommandResponse(
                            false,
                            run.getId(),
                            objectMapper.convertValue(result.state(), Object.class),
                            List.of(),
                            result.rejection().code().name(),
                            result.rejection().message()));
            }

            recordSecretCardUnlock(run, state, command, catalog);

            CombatOrchestrator.Advance advance = commandMetrics.record("ai", () ->
                    orchestrator.advanceAi(result.state()));
            CombatState current = advance.state();
            List<CombatEvent> events = new ArrayList<>(result.events());
            events.addAll(advance.events());

            if (current.status() == CombatStatus.FINISHED) {
                runtimeContextCache.invalidate(combatId);
            }

            return commandMetrics.record("serialize", () -> {
                run.setActiveCombat(objectMapper.valueToTree(current));
                syncPlayerHp(run, current);
                syncRunScopedRelicMemory(run, current);
                return new StsCombatCommandResponse(
                        true,
                        run.getId(),
                        objectMapper.convertValue(current, Object.class),
                        events.stream().map(this::serializeEvent).toList(),
                        null,
                        null);
            });
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "STS combat processing failed (run={}, command={}, expectedRevision={}, combatState={})",
                    run.getId(),
                    request.commandType(),
                    request.expectedRevision(),
                    run.getActiveCombat(),
                    failure);
            throw failure;
        }
    }

    /**
     * Ouvre le pool de cartes secrètes quand le joueur vient de jouer la carte qui l'ouvre.
     *
     * <p>La carte porteuse du marqueur {@code SecretUnlock} est lue dans la main d'<em>avant</em>
     * la commande : une fois jouée elle a quitté la main pour la défausse, l'exil, ou plus rien
     * du tout, et la chercher après revient à ne la trouver qu'une fois sur deux.
     *
     * <p>Le déverrouillage est porté par le compte et ne se reprend jamais, donc il ne s'écrit
     * qu'une fois : la garde n'est pas une optimisation, c'est ce qui évite de réécrire la date
     * de découverte à chaque carte rejouée.
     *
     * <p>Seule une commande acceptée compte — une carte refusée n'a pas été jouée — et seul le
     * combattant qui a envoyé la commande est fouillé : la même carte dans la main d'un autre
     * n'est pas celle que ce joueur a posée.
     */
    private void recordSecretCardUnlock(
            StsRun run, CombatState before, CombatCommand command, CardCatalog catalog) {
        if (!(command instanceof PlayCardCommand play)) {
            return;
        }

        AppUser owner = run.getOwner();
        if (owner == null || owner.hasUnlockedStsSecretCards()) {
            return;
        }

        CardInstance played = playedCard(before, play);
        if (played == null || !carriesSecretUnlock(played, catalog)) {
            return;
        }

        owner.setStsSecretCardsUnlockedAt(
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        LOGGER.info("STS secret card pool unlocked (run={}, card={})", run.getId(), played.definitionId());
    }

    /** @return la carte que cette commande a jouée, telle qu'elle était encore en main */
    private CardInstance playedCard(CombatState before, PlayCardCommand play) {
        if (before == null) {
            return null;
        }
        for (CombatantState combatant : before.combatants()) {
            if (!play.actorId().equals(combatant.combatantId())) {
                continue;
            }
            for (CardInstance card : combatant.piles().hand()) {
                if (play.cardInstanceId().equals(card.instanceId())) {
                    return card;
                }
            }
        }
        return null;
    }

    /**
     * Si cette copie porte le marqueur d'ouverture, marqueurs de la copie compris.
     *
     * <p>Passe par {@link EffectiveCard} plutôt que par la seule définition : une copie peut
     * s'être vu ajouter ou retirer le marqueur pendant le combat, et c'est ce qu'elle porte au
     * moment d'être jouée qui décide.
     */
    private boolean carriesSecretUnlock(CardInstance played, CardCatalog catalog) {
        CardDefinition definition = catalog == null ? null : catalog.find(played.definitionId());
        if (definition == null) {
            return played.tags().contains(CardTag.SECRET_UNLOCK)
                    && !played.removedTags().contains(CardTag.SECRET_UNLOCK);
        }
        return EffectiveCard.of(played, definition).hasTag(CardTag.SECRET_UNLOCK);
    }

    public AuthoritativeCompletionSnapshot snapshotForCompletion(StsRun run) {
        if (run == null || run.getActiveCombat() == null || run.getActiveCombat().isNull()) {
            return null;
        }

        CombatState state = requireCombatState(run.getActiveCombat());
        if (state.status() != CombatStatus.FINISHED) {
            return null;
        }

        if (state.combatId() != null) {
            runtimeContextCache.invalidate(state.combatId());
        }

        CombatantState player = requirePlayerCombatant(state);
        boolean victory = PLAYER_TEAM_ID.equals(state.winnerTeamId());
        return new AuthoritativeCompletionSnapshot(victory, player.hp(), player.maxHp());
    }

    boolean isRuntimeContextCached(UUID combatId) {
        return combatId != null && runtimeContextCache.getIfPresent(combatId) != null;
    }

    void clearRuntimeContextCache() {
        runtimeContextCache.invalidateAll();
    }

    void evictRuntimeContext(UUID combatId) {
        if (combatId != null) {
            runtimeContextCache.invalidate(combatId);
        }
    }

    private CombatSetup buildSetup(StsRun run, CardCatalog catalog) {
        UUID combatId = UUID.nameUUIDFromBytes((run.getId() + ":" + run.getActiveEncounter().path("encounterInstanceId").asText("")).getBytes(StandardCharsets.UTF_8));
        long seed = combatId.getMostSignificantBits() ^ combatId.getLeastSignificantBits();
        List<CombatantSetup> combatants = new ArrayList<>();
        combatants.add(playerSetup(run));
        combatants.addAll(enemySetups(run, catalog));
        return new CombatSetup(combatId, catalog.dataVersion(), seed, combatants);
    }

    private CombatantSetup playerSetup(StsRun run) {
        int maxHp = run.getPlayerState().path("maxHp").asInt(1);
        int startingHp = startingHp(run, maxHp);
        List<CardInstance> deck = new ArrayList<>();
        for (JsonNode cardNode : run.getRunInventory().path("deck")) {
            String instanceId = cardNode.path("instanceId").asText("").trim();
            String definitionId = cardNode.path("cardId").asText("").trim();
            if (instanceId.isBlank() || definitionId.isBlank()) {
                throw new IllegalArgumentException("run deck contains invalid card instances");
            }
            List<fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance> enchantments = new ArrayList<>();
            JsonNode enchNode = cardNode.path("enchantments");
            if (enchNode.isArray()) {
                for (JsonNode eNode : enchNode) {
                    enchantments.add(new fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance(
                        eNode.path("enchantmentId").asText("").trim(),
                        eNode.path("level").asInt(1)
                    ));
                }
            }
            deck.add(new CardInstance(instanceId, definitionId, enchantments));
        }
        List<String> relics = new ArrayList<>();
        if (run.getRunInventory() != null && run.getRunInventory().path("relics").isArray()) {
            for (JsonNode relicNode : run.getRunInventory().path("relics")) {
                String relicId = relicNode.path("relicId").asText("").trim();
                if (!relicId.isBlank()) {
                    relics.add(relicId);
                }
            }
        }
        return new CombatantSetup(
                PLAYER_COMBATANT_ID,
                PLAYER_TEAM_ID,
                ControllerType.HUMAN,
                maxHp,
                startingHp,
                DEFAULT_BASE_ENERGY,
                DEFAULT_TURN_DELAY_TICKS,
                0L,
                deck,
                relics,
                List.of(),
                0,
                runScopedRelicMemory(run, relics));
    }

    /**
     * Ce que les reliques du joueur rapportent des combats precedents.
     *
     * <p>Une cle jamais ecrite vaut ce que la relique declare valoir au depart : l'Autoreparation
     * ouvre une run avec ses 20 PV en reserve, et non a zero.
     */
    private List<fr.insastronaute.webapi.sts.combat.model.RelicMemory> runScopedRelicMemory(
            StsRun run, List<String> relicIds) {
        List<fr.insastronaute.webapi.sts.combat.model.RelicMemory> memory = new ArrayList<>();
        for (String relicId : relicIds) {
            Map<String, Integer> carried = relicEngine.runScopedMemory(relicId);
            if (carried.isEmpty()) {
                continue;
            }
            JsonNode stored = storedRelic(run, relicId);
            carried.forEach((key, initial) -> memory.add(
                    new fr.insastronaute.webapi.sts.combat.model.RelicMemory(
                            relicId, key,
                            stored != null && stored.hasNonNull(key)
                                    ? stored.path(key).asInt(initial)
                                    : initial)));
        }
        return memory;
    }

    /** Le noeud de l'inventaire qui decrit cette relique, ou null si la run ne la porte pas. */
    private static JsonNode storedRelic(StsRun run, String relicId) {
        if (run.getRunInventory() == null || !run.getRunInventory().path("relics").isArray()) {
            return null;
        }
        for (JsonNode relic : run.getRunInventory().path("relics")) {
            if (relicId.equals(relic.path("relicId").asText(""))) {
                return relic;
            }
        }
        return null;
    }

    /**
     * Reecrit dans la run ce que les reliques ont retenu et qui doit lui survivre.
     *
     * <p>Apres chaque commande, comme la sante du joueur : un combat quitte en cours de route ne
     * doit pas rendre au joueur une reserve qu'il a deja depensee.
     */
    private void syncRunScopedRelicMemory(StsRun run, CombatState state) {
        CombatantState player = requirePlayerCombatant(state);
        for (String relicId : player.relics()) {
            Map<String, Integer> carried = relicEngine.runScopedMemory(relicId);
            if (carried.isEmpty()) {
                continue;
            }
            if (!(storedRelic(run, relicId) instanceof ObjectNode relicNode)) {
                continue;
            }
            carried.keySet().forEach(key -> relicNode.put(key, player.recall(relicId, key)));
        }
    }

    /**
     * The health the player walks into the encounter with.
     *
     * <p>The run carries its wounds in {@code currentHp}, so a combat has to open on that rather
     * than on a full bar. A run that has not fought yet has no {@code currentHp} at all, and a
     * stored value that is missing, null or non-positive would open the combat on a corpse, so
     * either falls back to full health. A stale value above {@code maxHp} is capped, because
     * {@link CombatantSetup} refuses a starting health above the maximum.
     */
    private int startingHp(StsRun run, int maxHp) {
        JsonNode currentHp = run.getPlayerState().path("currentHp");
        if (!currentHp.isNumber() || currentHp.asInt() <= 0) {
            return maxHp;
        }
        return Math.min(currentHp.asInt(), maxHp);
    }

    private List<CombatantSetup> enemySetups(StsRun run, CardCatalog catalog) {
        ArrayNode enemies = gameDataService.enemies(run.getDataVersion());
        List<CombatantSetup> setups = new ArrayList<>();
        int index = 0;
        for (JsonNode enemyIdNode : run.getActiveEncounter().path("enemyIds")) {
            String enemyId = enemyIdNode.asText("").trim();
            JsonNode enemy = findEnemy(enemies, enemyId);
            String combatantId = "enemy-" + index;
            int maxHp = scaledEnemyMaxHp(run, enemy);

            // A pattern enemy plays from its move list rather than from a deck.
            setups.add(new CombatantSetup(
                    combatantId,
                    ENEMY_TEAM_ID,
                    ControllerType.AI,
                    maxHp,
                    0,
                    DEFAULT_TURN_DELAY_TICKS,
                    index + 1L,
                    List.of(),
                    List.of(),
                    startingStatuses(enemy),
                    startingPatternIndex(enemy, run, index)));
            index++;
        }
        return setups;
    }

    /**
     * The status an enemy walks in with.
     *
     * <p>An enemy declares one status, and it only counts when it actually holds something: a
     * declared status with neither a value nor a duration is an empty slot in the data, not a
     * status the enemy carries.
     *
     * <p>Not every status, though. A follow-up written with two zeroes is a follow-up that fires
     * on the first card and never runs out -- the ordinary way of writing one -- and a status of
     * FIXED shape carries no numbers at all. Both were being read as empty slots and dropped, so
     * an enemy authored to answer its own moves with a card simply never did, silently: nothing
     * reports a status that was never applied. {@code Enemy_1} and {@code Nemesis} are the two
     * this cost outright.
     */
    private static List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> startingStatuses(
            JsonNode enemy
    ) {
        String statusType = enemy.path("startingStatus").asText("").trim();
        int value = enemy.path("startingStatusValue").asInt(0);
        int duration = enemy.path("startingStatusDuration").asInt(0);
        if (statusType.isBlank()) {
            return List.of();
        }
        if (value == 0 && duration == 0
                && !fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog
                        .meansSomethingWithoutNumbers(statusType)) {
            return List.of();
        }
        return List.of(fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog.instantiate(
                statusType,
                value,
                duration,
                enemy.path("startingStatusInfo").asText(""),
                0));
    }

    /**
     * Where an enemy starts in its move pattern.
     *
     * <p>Most enemies open with their first move. One that starts at random picks by weight, from
     * a seed derived from the encounter, so the same encounter always opens the same way and a
     * reloaded combat does not shuffle its enemies' plans.
     */
    private static int startingPatternIndex(JsonNode enemy, StsRun run, int enemyIndex) {
        if (!enemy.path("randomStart").asBoolean(false)) {
            return 0;
        }
        List<Integer> weights = new ArrayList<>();
        for (JsonNode move : enemy.path("movePattern")) {
            boolean playable = !move.path("cardId").asText("").isBlank()
                    || move.path("effects").isArray() && !move.path("effects").isEmpty();
            weights.add(playable ? Math.max(1, move.path("weight").asInt(1)) : 0);
        }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return 0;
        }
        long seed = UUID.nameUUIDFromBytes((run.getId()
                + ":" + run.getActiveEncounter().path("encounterInstanceId").asText("")
                + ":" + enemyIndex).getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        int roll = new RandomState(seed).nextInt(total).value();
        int cumulative = 0;
        for (int index = 0; index < weights.size(); index++) {
            cumulative += weights.get(index);
            if (roll < cumulative) {
                return index;
            }
        }
        return 0;
    }

    /**
     * How much health an enemy actually walks in with.
     *
     * <p>The authored number is a base that the act and the chosen character scale, exactly as the
     * client does, so both sides agree on the enemy's health from the first frame of the combat.
     * A missing scaling table leaves the authored number alone rather than failing the combat.
     */
    private int scaledEnemyMaxHp(StsRun run, JsonNode enemy) {
        if (enemy == null || enemy.isNull()) {
            return 1;
        }

        int baseMaxHp = enemy.path("maxHP").asInt(1);

        JsonNode enemyPool = null;
        try {
            enemyPool = gameDataService.enemyPool(run.getDataVersion());
        } catch (Exception ignored) {
        }

        double multiplier = 1.0;
        if (enemyPool != null && !enemyPool.isNull()) {
            multiplier = enemyPool.path("baseHpScaling").asDouble(1.0);
            JsonNode actHpScaling = enemyPool.path("actHpScaling");
            if (actHpScaling.isArray() && !actHpScaling.isEmpty()) {
                int act = run.getAct() == null ? 0 : run.getAct();
                int actIndex = Math.min(Math.max(0, act), actHpScaling.size() - 1);
                multiplier += actHpScaling.get(actIndex).asDouble(0.0);
            }
        }

        String selectedCharacter = run.getSelectedCharacter();
        JsonNode character = null;
        if (selectedCharacter != null && !selectedCharacter.isBlank()) {
            try {
                character = gameDataService.character(run.getDataVersion(), selectedCharacter);
            } catch (Exception ignored) {
            }
        }
        if (character != null && !character.isNull()) {
            multiplier += character.path("hpAdditionalMultiplier").asDouble(0.0);
        }

        return (int) Math.round(baseMaxHp * multiplier);
    }

    /**
     * La politique qui joue les ennemis de cette run.
     *
     * <p>La lecture des repertoires elle-meme vit dans {@link EnemyPatternReader}, parce qu'un
     * boss de raid est un ennemi des memes donnees joue par la meme politique : deux lectures
     * auraient fini par diverger, et le meme ennemi se serait battu differemment selon l'endroit
     * ou on le rencontre. Ce qui reste ici est ce que le raid n'a pas — la run, son encounter,
     * et la convention {@code enemy-<i>} qui nomme ses combattants.
     */
    private fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy policyForRun(StsRun run, CardCatalog catalog) {
        Map<String, List<fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy.EnemyMoveNode>> patterns = new LinkedHashMap<>();
        ArrayNode enemies = gameDataService.enemies(run.getDataVersion());
        int index = 0;
        for (JsonNode enemyIdNode : run.getActiveEncounter().path("enemyIds")) {
            String enemyId = enemyIdNode.asText("").trim();
            JsonNode enemy = EnemyPatternReader.findEnemy(enemies, enemyId);
            String combatantId = "enemy-" + index;

            // Un motif est reconstruit a chaque commande, pas une fois par combat : sans cette
            // retenue, un ennemi mal declare repeterait les memes lignes des dizaines de fois
            // par combat, et pour chaque joueur qui le rencontre.
            boolean report = diagnosedEnemies.add(
                    run.getDataVersion() + "|" + enemyId + "|" + combatantId);

            EnemyPatternReader.Pattern pattern = EnemyPatternReader.read(
                    catalog,
                    enemy,
                    enemyId,
                    enemyMoveParser,
                    report
                            ? new RunEnemyDiagnostics(run.getDataVersion(), combatantId)
                            : EnemyPatternReader.Diagnostics.SILENT);

            patterns.put(combatantId, pattern.moves());
            index++;
        }
        return new fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy(catalog, patterns);
    }

    /** Ce qu'on dit d'un ennemi de run mal declare, et sous quel nom on le dit. */
    private static final class RunEnemyDiagnostics implements EnemyPatternReader.Diagnostics {

        private final String dataVersion;
        private final String combatantId;

        private RunEnemyDiagnostics(String dataVersion, String combatantId) {
            this.dataVersion = dataVersion;
            this.combatantId = combatantId;
        }

        @Override
        public void moveDropped(String enemyId, String field, int moveIndex, String reason) {
            LOGGER.warn(
                    "Dropping enemy '{}'.{}[{}] (combatant {}, data version {}): {}.",
                    enemyId, field, moveIndex, combatantId, dataVersion, reason);
        }

        /**
         * Une erreur et non un avertissement : un ennemi qui ne peut jamais agir vide une
         * rencontre entiere de son contenu. Le combat reste jouable pour autant, et cette ligne
         * est la seule trace que le defaut laissera.
         */
        @Override
        public void noUsableMove(String enemyId, int declaredMoves) {
            LOGGER.error(
                    "Enemy '{}' (combatant {}, data version {}) has no usable move: all {}"
                            + " declared move(s) were dropped. It will end every turn without"
                            + " acting and the encounter will play out as if it were not there.",
                    enemyId, combatantId, dataVersion, declaredMoves);
        }
    }

    private CardCatalog catalogForRun(StsRun run) {
        ArrayNode enemies = gameDataService.enemies(run.getDataVersion());
        // The encounter's enemies go to the factory too: one that starts with a follow-up naming
        // no card plays a card from the whole game, which the catalog then has to hold.
        List<JsonNode> encounterEnemies = new ArrayList<>();
        for (JsonNode enemyIdNode : run.getActiveEncounter().path("enemyIds")) {
            JsonNode enemy = findEnemy(enemies, enemyIdNode.asText("").trim());
            if (enemy != null) {
                encounterEnemies.add(enemy);
            }
        }
        CardCatalog baseCatalog = cardCatalogFactory.create(
                gameDataService.cards(run.getDataVersion()),
                run.getDataVersion(),
                runCardReferences(run),
                run.getSelectedCharacter(),
                encounterEnemies);
        Map<String, CardDefinition> merged = new LinkedHashMap<>(baseCatalog.cards());
        for (JsonNode enemyIdNode : run.getActiveEncounter().path("enemyIds")) {
            String enemyId = enemyIdNode.asText("").trim();
            JsonNode enemy = findEnemy(enemies, enemyId);

            JsonNode movePattern = enemy.path("movePattern");
            if (movePattern.isArray()) {
                for (int index = 0; index < movePattern.size(); index++) {
                    JsonNode move = movePattern.get(index);
                    CardDefinition definition = enemyMoveParser.parseMove(baseCatalog, move, enemyId, index);
                    if (definition != null) {
                        merged.put(definition.id(), definition);
                    }
                }
            }
        }
        return new CardCatalog(baseCatalog.dataVersion(), baseCatalog.character(), merged);
    }

    /**
     * Every card this combat names up front: the run's deck, the moves its enemies can play, and
     * whatever its relics can conjure.
     *
     * <p>What those cards themselves create is followed by {@link CardCatalogFactory}, which owns
     * that rule for PvE and PvP alike so the two cannot drift.
     *
     * <p>The relics were the gap. A combat's catalog is scoped to what the combat names, and a
     * card a relic invents out of nothing is named by no deck and no enemy move: MRIE's Météorite
     * and Galaxie reached the piles with no definition behind them and were refused the moment
     * they were played, with UNSUPPORTED_EFFECT. Handlers declare what they create; this asks.
     */
    private List<String> runCardReferences(StsRun run) {
        List<String> references = new ArrayList<>();
        for (JsonNode card : run.getRunInventory().path("deck")) {
            references.add(card.path("cardId").asText(""));
        }

        List<String> relicIds = new ArrayList<>();
        for (JsonNode relic : run.getRunInventory().path("relics")) {
            String relicId = relic.isTextual()
                    ? relic.asText("")
                    : relic.path("relicId").asText("");
            if (!relicId.isBlank()) {
                relicIds.add(relicId);
            }
        }
        references.addAll(relicEngine.generatedCardReferences(relicIds));

        ArrayNode enemies = gameDataService.enemies(run.getDataVersion());
        for (JsonNode enemyIdNode : run.getActiveEncounter().path("enemyIds")) {
            JsonNode enemy = findEnemy(enemies, enemyIdNode.asText("").trim());
            for (JsonNode move : enemy.path("movePattern")) {
                references.add(move.path("cardId").asText(""));
                for (JsonNode effect : move.path("effects")) {
                    references.add(effect.path("cardID").asText(""));
                }
            }
            for (JsonNode cardIdNode : enemy.path("patternCardIds")) {
                references.add(cardIdNode.asText(""));
            }
        }
        return references;
    }

    private CombatCommand commandFromRequest(CombatState state, StsCombatCommandRequest request) {
        if (request.commandType() == null || request.commandType().isBlank()) {
            throw new BadRequestException("Le type de commande de combat STS est requis.");
        }
        if (request.expectedRevision() == null) {
            throw new BadRequestException("La révision attendue du combat STS est requise.");
        }

        String commandType = request.commandType().trim().toUpperCase();
        // The player side may field several combatants; the one whose turn is active acts.
        String actorId = state.combatants().stream()
                .filter(combatant -> PLAYER_TEAM_ID.equals(combatant.teamId()))
                .filter(combatant -> combatant.combatantId().equals(state.activeCombatantId()))
                .map(CombatantState::combatantId)
                .findFirst()
                .orElse(PLAYER_COMBATANT_ID);
        return switch (commandType) {
            case "PLAY_CARD" -> new PlayCardCommand(
                    state.combatId(),
                    request.expectedRevision(),
                    actorId,
                    requireNonBlank(request.cardInstanceId(), "La carte jouée est requise."),
                    request.targetIds(),
                    request.selectedCardInstanceIds());
            case "END_TURN" -> new EndTurnCommand(
                    state.combatId(),
                    request.expectedRevision(),
                    actorId);
            default -> throw new BadRequestException("Type de commande de combat STS inconnu.");
        };
    }

    private CombatState requireCombatState(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, CombatState.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted combat state", exception);
        }
    }

    private CombatantState requirePlayerCombatant(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> PLAYER_COMBATANT_ID.equals(combatant.combatantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("player combatant is missing"));
    }

    private void syncPlayerHp(StsRun run, CombatState state) {
        CombatantState player = requirePlayerCombatant(state);
        JsonNode playerState = run.getPlayerState();
        if (playerState instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("currentHp", player.hp());
        }
    }

    private JsonNode findEnemy(ArrayNode enemies, String enemyId) {
        return EnemyPatternReader.findEnemy(enemies, enemyId);
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private Object serializeEvent(CombatEvent event) {
        ObjectNode node = objectMapper.valueToTree(event);
        node.put("eventType", event.getClass().getSimpleName());
        return objectMapper.convertValue(node, Object.class);
    }

    /**
     * What the run keeps of a combat that has ended.
     *
     * <p>{@code playerMaxHpAfter} is here because a combat can raise it -- equipment that trades
     * something for max health -- and the run used to write back the current health alone. The
     * gain vanished with the combat that granted it, and the old ceiling then went on to cap the
     * health carried forward and to refuse a completion that reported more health than it allowed.
     */
    public record AuthoritativeCompletionSnapshot(
            boolean victory,
            int playerHpAfter,
            int playerMaxHpAfter) {
    }
}
