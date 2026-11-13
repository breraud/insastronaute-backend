package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.CombatOrchestrator;
import fr.insastronaute.webapi.sts.combat.ai.EnemyPatternReader;
import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.SingleCardEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EnemyMoveParser;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatSetupFactory.StsPvpParticipantSetup;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a PvP battle on the authoritative combat engine.
 *
 * <p>Deliberately not the {@code CombatOrchestrator}: its job is to auto-play the turns of
 * non-human combatants, which is exactly what must not happen here. Both PvP combatants are
 * human, so when the timeline hands the turn to the other player the server simply waits for
 * their command.
 *
 * <p>The engine owns the end of the combat. It computes the surviving team, marks the state
 * {@code FINISHED} and refuses every later command; this class only reads that verdict off the
 * state it gets back and writes it onto the battle.
 */
@Service
public class StsPvpCombatService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    /** How long a player has to act before the turn can be taken from them. */
    public static final long TURN_TIMEOUT_SECONDS = 30L;

    private static final int PVP_TEAM_COUNT = 2;

    private final EnemyMoveParser enemyMoveParser = new EnemyMoveParser();

    private final ObjectMapper objectMapper;
    private final StsGameDataService gameDataService;
    private final StsPvpDeckRepository deckRepository;
    private final StsPvpProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final EnchantmentEngine enchantmentEngine;

    private final CardCatalogFactory cardCatalogFactory = new CardCatalogFactory();
    /**
     * The same handlers a run uses. Duels started with none at all -- every combatant entered
     * relicless, so no handler could run and the engine was built empty to say so. Now that a
     * duelist carries the base relic of the character their deck is built around, the engine has
     * to know those handlers or the relic would be an id on a combatant and nothing more.
     */
    private final RelicEngine relicEngine;

    public StsPvpCombatService(
            ObjectMapper objectMapper,
            StsGameDataService gameDataService,
            StsPvpDeckRepository deckRepository,
            StsPvpProfileRepository profileRepository,
            UserRepository userRepository,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
                List<RelicHandler> relicHandlers,
                EnchantmentEngine enchantmentEngine
    ) {
        this.relicEngine = new RelicEngine(relicHandlers);
        this.objectMapper = objectMapper;
        this.gameDataService = gameDataService;
        this.deckRepository = deckRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.enchantmentEngine = enchantmentEngine;
    }

    /** The state and the events one accepted command produced, and whether it ended the combat. */
    public record StsPvpCommandOutcome(CombatState state, List<CombatEvent> events, boolean finished) {

        public StsPvpCommandOutcome {
            Objects.requireNonNull(state, "state");
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    /**
     * Opens the combat of a battle: both players' active decks become the two combatants, and the
     * resulting authoritative state is stored on the battle, which becomes {@code ACTIVE}.
     */
    public CombatState startCombat(StsPvpBattle battle) {
        Objects.requireNonNull(battle, "battle");
        if (battle.getId() == null) {
            throw new IllegalArgumentException("a PvP battle must be persisted before its combat starts");
        }

        List<StsPvpSeat> seats = seats(battle);
        String dataVersion = gameDataService.latestVersion();

        // Le deck de chaque siege, resolu une fois : un renfort emprunte celui d'un joueur de son
        // equipe, et le demander deux fois pourrait rendre deux decks differents si le joueur en
        // changeait entre-temps.
        Map<String, List<String>> decksBySeat = new LinkedHashMap<>();
        for (StsPvpSeat seat : seats) {
            decksBySeat.put(seat.combatantId(), deckFor(seats, seat));
        }

        List<StsPvpParticipantSetup> setups = new ArrayList<>();
        StsPvpRatingRules rules = ratingRules();
        for (StsPvpSeat seat : seats) {
            setups.add(new StsPvpParticipantSetup(
                    seat.combatantId(),
                    decksBySeat.get(seat.combatantId()),
                    seat.isBoss() ? null : seat.characterOrDefault(),
                    seat.teamIndex(),
                    seat.ai() ? ControllerType.AI : ControllerType.HUMAN,
                seat.isBoss() ? bossMaxHp(dataVersion, seat, seats) : playerMaxHp(dataVersion, seat, rules)));
        }

        CombatSetup setup = StsPvpCombatSetupFactory.create(
                battle.getId(), dataVersion, battle.getId().getMostSignificantBits(), setups);

        List<String> deckCardIds = new ArrayList<>();
        decksBySeat.values().forEach(deckCardIds::addAll);
        // The relics the duelists now bring can conjure cards their decks never named, and a
        // card in a pile with no definition behind it is refused the moment it is played. They go
        // into the catalog here, before the factory runs the relics that create them.
        deckCardIds.addAll(relicEngine.generatedCardReferences(
                setup.combatants().stream()
                        .flatMap(combatant -> combatant.relics().stream())
                        .toList()));
        CardCatalog catalog = catalog(dataVersion, deckCardIds, seats);

        CombatCreated created = new CombatFactory(catalog, relicEngine).create(setup);
        CombatState state = created.state();

        // Le boss, ou un renfort, peut avoir la main des l'ouverture : sans cette avance, le
        // combat attendrait la commande d'un joueur qui n'a pas le droit de jouer. Elle pose
        // aussi les intentions, ce qui est ce que le client affiche au-dessus du boss.
        CombatOrchestrator.Advance opening = advanceAi(battle, state, catalog, seats);
        state = opening.state();

        storeState(battle, state);
        // Ouvert explicitement : la colonne a un defaut en base, mais une bataille construite
        // en memoire -- tous les tests le sont -- n'en beneficie pas, et l'historique partirait
        // null jusqu'a la premiere carte jouee.
        battle.setCardHistory(objectMapper.createArrayNode());
        recordPlayedCards(battle, opening.events());
        battle.setStatus(StsPvpBattleStatus.ACTIVE);
        battle.setTurnDeadline(TimeTravelContext.instantNow().plus(TURN_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
        return state;
    }

    /**
     * Les cartes que ce siege apporte au combat.
     *
     * <p>Un boss n'en apporte aucune : il joue le motif de sa definition. Un renfort joue le deck
     * du joueur dont il tient la place, ce qui lui garantit un deck legal sans qu'il faille ecrire
     * du contenu pour lui.
     */
    private List<String> deckFor(List<StsPvpSeat> seats, StsPvpSeat seat) {
        if (seat.isBoss()) {
            return List.of();
        }
        if (seat.deckOwnerUserId() == null) {
            return activeDeckCardIds(seat);
        }
        StsPvpSeat owner = seats.stream()
                .filter(candidate -> seat.deckOwnerUserId().equals(candidate.userId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Le renfort de ce combat PVP n'a pas de deck a emprunter."));
        return activeDeckCardIds(owner);
    }

    /**
     * Les points de vie du boss d'un raid : ceux de sa definition, mis a l'echelle du nombre de
     * joueurs qui l'affrontent, puis multiplies par l'echelle propre a sa rencontre
     * ({@code hpMultiplier} dans {@code gameplay/pvp_raid_encounters.json}).
     */
    private int bossMaxHp(String dataVersion, StsPvpSeat boss, List<StsPvpSeat> seats) {
        JsonNode enemy = EnemyPatternReader.findEnemy(
                gameDataService.enemies(dataVersion), boss.bossEnemyId());
        int baseMaxHp = enemy.path("maxHP").asInt(1);
        long players = seats.stream()
                .filter(seat -> !seat.ai())
                .filter(seat -> seat.teamIndex() != boss.teamIndex())
                .count();
        return Math.max(1, (int) Math.round(baseMaxHp * Math.max(1L, players) * boss.bossHpMultiplier()));
    }

    /** Les PV d'un joueur PVP : ceux de son personnage, multiplies par le barème du format. */
    private int playerMaxHp(String dataVersion, StsPvpSeat seat, StsPvpRatingRules rules) {
        JsonNode character = gameDataService.character(dataVersion, seat.characterOrDefault());
        int baseMaxHp = firstPositiveInt(character, "hp", "maxHp", "maxHP")
                .orElse(StsPvpCombatSetupFactory.PVP_MAX_HP);
        return Math.max(1, (int) Math.round(baseMaxHp * rules.playerHpMultiplier()));
    }

    private Optional<Integer> firstPositiveInt(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return Optional.empty();
        }
        for (String key : keys) {
            int value = node.path(key).asInt(0);
            if (value > 0) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** The authoritative state a battle currently holds. */
    public CombatState currentState(StsPvpBattle battle) {
        return currentStateIfAny(battle).orElseThrow(
                () -> new ConflictException("Ce combat PVP n'a pas encore d'état autoritatif."));
    }

    /**
     * The authoritative state a battle currently holds, when its combat has already started.
     *
     * <p>A battle still waiting for its combat carries a placeholder instead, which is a fact to
     * report rather than an error: reading such a battle is legitimate, it simply has no combat to
     * show yet. A state that exists but cannot be read is another matter, and still throws.
     */
    public Optional<CombatState> currentStateIfAny(StsPvpBattle battle) {
        Objects.requireNonNull(battle, "battle");
        JsonNode state = battle.getState();
        if (state == null || !state.isObject() || !state.hasNonNull("combatId")) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.treeToValue(state, CombatState.class));
        } catch (Exception exception) {
            throw new ConflictException("L'état de ce combat PVP est illisible.");
        }
    }

    /** Writes {@code state} onto the battle as its authoritative combat. */
    public void storeState(StsPvpBattle battle, CombatState state) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(state, "state");
        battle.setState(objectMapper.valueToTree(state));
    }

    /**
     * Ajoute à l'historique partagé du duel les cartes que {@code events} a vu jouer.
     *
     * <p>Appelé sur les événements d'une commande acceptée, quelle qu'en soit l'origine : le
     * coup d'un joueur comme les tours qu'une IA enchaîne derrière lui. C'est ce qui garde
     * l'historique complet dans un raid, où le boss joue sans qu'aucun joueur n'ait envoyé de
     * commande pour lui.
     */
    public void recordPlayedCards(StsPvpBattle battle, List<CombatEvent> events) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(events, "events");
        battle.setCardHistory(
                StsPvpCardHistory.append(objectMapper, battle.getCardHistory(), events));
    }

    /** L'historique partagé du duel, relu, le plus ancien d'abord. */
    public List<StsPvpCardHistory.Entry> cardHistory(StsPvpBattle battle) {
        Objects.requireNonNull(battle, "battle");
        return StsPvpCardHistory.read(battle.getCardHistory());
    }

    /**
     * Records that {@code userId} was heard from just now.
     *
     * <p>Every entry point a player can reach the battle through goes past this method, and that
     * is the point: the stamp used to live in the HTTP endpoint alone, so a player who played the
     * whole combat over the socket looked as silent as one who had closed their laptop, and the
     * disconnect sweep would forfeit them mid-match. Stamping twice in one request is harmless —
     * the key is the player and the value only ever moves forward.
     */
    public void recordHeartbeat(StsPvpBattle battle, UUID userId) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(userId, "userId");

        ObjectNode heartbeat = battle.getHeartbeat() != null && battle.getHeartbeat().isObject()
                ? (ObjectNode) battle.getHeartbeat().deepCopy()
                : objectMapper.createObjectNode();
        heartbeat.put(userId.toString(), TimeTravelContext.instantNow().toEpochMilli());
        battle.setHeartbeat(heartbeat);
    }

    /**
     * Applies one player's command to the battle's combat.
     *
     * <p>The command is refused unless it carries the current revision and comes from the
     * combatant the engine's timeline made active. What the engine then answers is authoritative:
     * a rejection is reported as such, and a state that comes back {@code FINISHED} settles the
     * battle and moves both players' rating.
     */
    public StsPvpCommandOutcome applyCommand(StsPvpBattle battle, UUID actingUserId, CombatCommand command) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(actingUserId, "actingUserId");
        Objects.requireNonNull(command, "command");

        // A battle a sweep has already settled — a forfeit, an abandon — takes no more commands:
        // the rating has moved and playing on would move it a second time.
        if (battle.getStatus() != StsPvpBattleStatus.ACTIVE) {
            throw new ConflictException("Ce combat PVP n'est plus en cours.");
        }

        CombatState state = currentState(battle);
        StsPvpTurnGuard.requireCurrentRevision(state, command.expectedRevision());
        StsPvpTurnGuard.requireActiveCombatant(state, actingUserId.toString());
        if (!actingUserId.toString().equals(command.actorId())) {
            throw new BadRequestException("Cette commande n'est pas émise au nom de son joueur.");
        }

        List<StsPvpSeat> seats = seats(battle);
        CardCatalog catalog = catalog(battle, state, seats);
        CombatResult result = new CombatEngine(catalog, relicEngine, enchantmentEngine)
            .handle(state, command);
        if (!result.accepted()) {
            // The engine's own code travels with the refusal. Flattening it into a bare bad
            // request lost the one thing the player needs to know: which rule they broke.
            throw new StsPvpCommandRejectedException(
                    result.rejection().code(), result.rejection().message());
        }

        // Les combattants que le serveur pilote jouent dans la foulee de la commande qui leur
        // rend la main : un boss de raid, un renfort qui tient un siege vide. Sans cela le
        // combat s'arreterait sur leur tour, a attendre une commande que personne n'a le droit
        // d'envoyer. L'orchestrateur s'arrete de lui-meme des que le tour revient a un humain.
        CombatOrchestrator.Advance advance = advanceAi(battle, result.state(), catalog, seats);
        CombatState resultingState = advance.state();
        List<CombatEvent> events = new ArrayList<>(result.events());
        events.addAll(advance.events());

        storeState(battle, resultingState);
        recordPlayedCards(battle, events);
        battle.setTurnDeadline(TimeTravelContext.instantNow().plus(TURN_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
        // Playing is proof of presence, whichever way the command came in.
        recordHeartbeat(battle, actingUserId);

        // The engine has already decided this; never re-derive it.
        boolean finished = resultingState.status() == CombatStatus.FINISHED;
        if (finished) {
            settle(battle, resultingState);
        }
        return new StsPvpCommandOutcome(resultingState, events, finished);
    }

    /**
     * Fait jouer les combattants que le serveur pilote, jusqu'a ce que la main revienne a un
     * joueur.
     *
     * <p>C'est le {@link CombatOrchestrator} de la PvE, employe ici pour la raison meme qui le
     * faisait ecarter du duel : son travail est de jouer les tours des combattants non humains.
     * Un duel n'en a aucun et l'appeler n'y change rien — il rend l'etat tel quel des que
     * l'actif est humain — tandis qu'un raid en a un et ne se jouerait pas sans lui.
     */
    private CombatOrchestrator.Advance advanceAi(
            StsPvpBattle battle, CombatState state, CardCatalog catalog, List<StsPvpSeat> seats) {
        boolean anyAi = seats.stream().anyMatch(StsPvpSeat::ai);
        if (!anyAi) {
            return new CombatOrchestrator.Advance(state, List.of());
        }
        return new CombatOrchestrator(
            new CombatEngine(catalog, relicEngine, enchantmentEngine), policyFor(catalog, seats))
                .advanceAi(state);
    }

    /**
     * Qui joue les combattants du serveur : les boss suivent leur motif, les renforts leur deck.
     *
     * @see StsPvpEnemyPolicy
     */
    private EnemyPolicy policyFor(CardCatalog catalog, List<StsPvpSeat> seats) {
        Map<String, List<PatternEnemyPolicy.EnemyMoveNode>> patterns = new LinkedHashMap<>();
        Set<String> bosses = new LinkedHashSet<>();
        ArrayNode enemies = null;
        for (StsPvpSeat seat : seats) {
            if (!seat.isBoss()) {
                continue;
            }
            if (enemies == null) {
                enemies = gameDataService.enemies(catalog.dataVersion());
            }
            JsonNode enemy = EnemyPatternReader.findEnemy(enemies, seat.bossEnemyId());
            // Le motif est relu a chaque commande, comme en run : signaler un boss mal declare
            // ici ecrirait une ligne par coup joue.
            patterns.put(seat.combatantId(), EnemyPatternReader.read(
                    catalog, enemy, seat.bossEnemyId(), enemyMoveParser,
                    EnemyPatternReader.Diagnostics.SILENT).moves());
            bosses.add(seat.combatantId());
        }
        return new StsPvpEnemyPolicy(
                bosses,
                new PatternEnemyPolicy(catalog, patterns),
                new SingleCardEnemyPolicy(catalog));
    }

    /**
     * Writes the engine's verdict onto the battle and moves the two players' rating.
     *
     * <p>{@link StsPvpBattleSettlement#winningTeamIndex} is only asked which team won, never
     * whether the combat is over — a draw wipes out both teams and leaves no winner at all.
     */
    private void settle(StsPvpBattle battle, CombatState state) {
        battle.setStatus(StsPvpBattleStatus.FINISHED);
        battle.setTurnDeadline(null);

        OptionalInt winningTeam = StsPvpBattleSettlement.winningTeamIndex(state);
        if (winningTeam.isEmpty()) {
            battle.setWinnerTeam(null);
            return;
        }
        battle.setWinnerTeam(winningTeam.getAsInt());
        settleRewards(battle, winningTeam.getAsInt());
    }

    /**
     * Settles the battle {@code forfeitingUserId} has just given up on, and hands back what that
     * has to be announced with.
     *
     * <p>Giving up is not a move the engine has a command for, so the outcome is written rather
     * than resolved — which is exactly what {@link StsPvpBattleSettlement#conceded} already does
     * for the player the timeout sweep decides has left. A player who says so out loud and a
     * player who simply stops answering end the same duel the same way, and they end it here in
     * one place so the two cannot drift apart.
     *
     * <p>Nothing is announced and no rating moves when there was nothing left to concede. The
     * battle's own status answers that for a duel already settled, and {@code conceded} answers it
     * for a combat the engine had already ended on the table: it hands the state back untouched
     * with nothing to say, and that emptiness is read here rather than the status being judged a
     * second time.
     *
     * @return the conceded combat and its events, or empty when the duel was already over
     * @throws BadRequestException when {@code forfeitingUserId} does not play in this battle
     */
    public Optional<StsPvpBattleSettlement.Conceded> concede(StsPvpBattle battle, UUID forfeitingUserId) {
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(forfeitingUserId, "forfeitingUserId");

        if (battle.getStatus() != StsPvpBattleStatus.ACTIVE) {
            return Optional.empty();
        }
        List<StsPvpSeat> seats = seats(battle);
        StsPvpSeat quitter = seatOf(seats, forfeitingUserId);

        Optional<StsPvpBattleSettlement.Conceded> conceded = currentStateIfAny(battle)
                .map(state -> StsPvpBattleSettlement.conceded(state, quitter.combatantId()));
        if (conceded.isEmpty() || !conceded.get().applied()) {
            return Optional.empty();
        }

        StsPvpBattleSettlement.Conceded outcome = conceded.get();
        storeState(battle, outcome.state());

        if (!outcome.finished()) {
            // En equipe, un joueur qui abandonne quitte le combat et son equipier continue.
            // Terminer ici mettrait fin a la partie de quelqu'un qui n'a rien abandonne, et
            // deplacerait un classement sur une bataille encore en cours.
            battle.setTurnDeadline(
                    TimeTravelContext.instantNow().plus(TURN_TIMEOUT_SECONDS, ChronoUnit.SECONDS));
            return conceded;
        }

        battle.setStatus(StsPvpBattleStatus.FINISHED);
        battle.setTurnDeadline(null);
        int winnerTeam = otherTeamIndex(seats, quitter.teamIndex());
        battle.setWinnerTeam(winnerTeam);
        // Conceding is losing: the rating moves the way it moves for a player who was not there.
        // Undoing that decision means not calling this line, and nothing else.
        settleRewards(battle, winnerTeam);
        return conceded;
    }

    /** Le siege que {@code userId} occupe dans cette bataille. */
    private static StsPvpSeat seatOf(List<StsPvpSeat> seats, UUID userId) {
        for (StsPvpSeat seat : seats) {
            if (userId.equals(seat.userId())) {
                return seat;
            }
        }
        // Nothing of the combat travels with the refusal: the stored state holds every hand and
        // the seed, and a refusal that quoted any of it would be a way to read what it refused.
        throw new BadRequestException("Ce joueur ne participe pas a ce combat PVP.");
    }

    /** L'autre equipe, une bataille en opposant exactement deux. */
    private static int otherTeamIndex(List<StsPvpSeat> seats, int teamIndex) {
        return seats.stream()
                .mapToInt(StsPvpSeat::teamIndex)
                .filter(candidate -> candidate != teamIndex)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Ce combat PVP n'oppose pas deux equipes."));
    }

    /**
     * Règle le duel pour tout le monde : classement, jetons, et la trace de ce que ça a valu.
     *
     * <p>Publique parce que deux chemins mènent ici — le combat que le moteur termine, et le
     * forfait que la veille des absents prononce — et qu'ils doivent payer la même chose. Ils en
     * avaient chacun une copie, ce qui est exactement la façon dont deux barèmes finissent par
     * diverger sans que personne le remarque.
     *
     * <p><b>Un raid ne classe pas et ne paie pas.</b> Le classement mesure des joueurs les uns
     * contre les autres, et un raid se joue contre un boss que le serveur pilote : l'équipe d'en
     * face n'a personne à qui prendre des points, et en distribuer quand même reviendrait à payer
     * pour avoir battu le serveur.
     *
     * <p>Un joueur sans profil arrête tout : personne n'est réglé à moitié.
     */
    public void settleRewards(StsPvpBattle battle, int winningTeamIndex) {
        Objects.requireNonNull(battle, "battle");

        if (battle.getMode().hasBoss()) {
            announceVictory(battle, winningTeamIndex);
            return;
        }

        List<StsPvpSeat> seats = seats(battle);
        List<StsPvpBattleSettlement.RatedPlayer> winners = new ArrayList<>();
        List<StsPvpBattleSettlement.RatedPlayer> losers = new ArrayList<>();
        for (StsPvpSeat seat : seats) {
            if (seat.ai()) {
                continue;
            }
            Optional<StsPvpProfile> profile = profile(seat);
            if (profile.isEmpty()) {
                return;
            }
            StsPvpBattleSettlement.RatedPlayer rated =
                    new StsPvpBattleSettlement.RatedPlayer(seat.userId(), profile.get());
            (seat.teamIndex() == winningTeamIndex ? winners : losers).add(rated);
        }
        if (winners.isEmpty() || losers.isEmpty()) {
            return;
        }

        List<StsPvpBattleRewards.Entry> rewards = StsPvpBattleSettlement.applyTeamRating(
                winners, losers, Boolean.TRUE.equals(battle.getFriendly()), ratingRules());

        winners.forEach(player -> profileRepository.save(player.profile()));
        losers.forEach(player -> profileRepository.save(player.profile()));

        // Les jetons sont versés ici et non dans le calcul : lui ne connaît pas les
        // portefeuilles, et le solde est ce qui décide de ce qu'une défaite peut réellement
        // retirer. Ce qui est rangé sur la bataille est donc le montant appliqué, pas le montant
        // théorique — annoncer un retrait qui n'a pas eu lieu serait pire que de se taire.
        List<StsPvpBattleRewards.Entry> applied = new ArrayList<>();
        for (StsPvpBattleRewards.Entry reward : rewards) {
            applied.add(payTokens(reward));
        }
        battle.setRewards(StsPvpBattleRewards.write(objectMapper, applied));

        announceVictory(battle, winningTeamIndex);
    }

    /**
     * Le barème en vigueur, relu à chaque règlement.
     *
     * <p>Pas mis en cache : une publication de données change les chiffres, et un barème gardé en
     * mémoire ferait cohabiter deux tarifs jusqu'au prochain redémarrage. Un règlement par duel,
     * c'est une lecture de fichier déjà mise en cache plus bas.
     */
    private StsPvpRatingRules ratingRules() {
        return StsPvpRatingRules.from(gameDataService.pvpRating(gameDataService.latestVersion()));
    }

    /**
     * Verse à un joueur ce que le duel lui a rapporté, sans jamais passer sous zéro.
     *
     * @return la même récompense, son montant de jetons ramené à ce qui a pu être appliqué
     */    private StsPvpBattleRewards.Entry payTokens(StsPvpBattleRewards.Entry reward) {
        if (reward.tokenDelta() == 0L) {
            return reward;
        }

        Optional<AppUser> user = userRepository.findById(reward.userId());
        if (user.isEmpty()) {
            return new StsPvpBattleRewards.Entry(
                    reward.userId(), reward.won(), reward.eloBefore(), reward.eloAfter(),
                    reward.eloDelta(), 0L);
        }

        AppUser account = user.get();
        long before = account.getTokens() == null ? 0L : account.getTokens();
        long after = Math.max(0L, Math.addExact(before, reward.tokenDelta()));
        account.setTokens(after);
        long battleEventPoints = account.creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource.PVP_BATTLE, after - before);
        currencyLedger.record(account, fr.insastronaute.webapi.economy.domain.CurrencySource.PVP_BATTLE, after - before, battleEventPoints, null);
        userRepository.save(account);

        return new StsPvpBattleRewards.Entry(
                reward.userId(), reward.won(), reward.eloBefore(), reward.eloAfter(),
                reward.eloDelta(), after - before);
    }

    /** Dit aux succes que ces joueurs ont gagne. Le siege porte deja l'identifiant. */
    private void announceVictory(StsPvpBattle battle, int winningTeamIndex) {
        for (StsPvpSeat seat : seats(battle)) {
            if (!seat.ai() && seat.teamIndex() == winningTeamIndex) {
                autoChallengeTriggerPublisher.publish(
                        seat.userId(), AutoChallengeTrigger.STS_PVP_BATTLE_SETTLED);
            }
        }
    }

    /** The catalog a running combat needs: the cards its combatants actually hold. */
    private CardCatalog catalog(StsPvpBattle battle, CombatState state, List<StsPvpSeat> seats) {
        Set<String> cardIds = new LinkedHashSet<>();
        for (CombatantState combatant : state.combatants()) {
            for (List<CardInstance> pile : List.of(
                    combatant.piles().draw(),
                    combatant.piles().hand(),
                    combatant.piles().discard(),
                    combatant.piles().exhaust())) {
                pile.forEach(card -> cardIds.add(card.definitionId()));
            }
        }
        // Les cartes qu'une relique invente ne sont dans aucune pile au moment ou l'on batit le
        // catalogue : MRIE pose les siennes a l'ouverture du combat, et une carte absente du
        // catalogue est refusee des qu'on la joue. La PvE verse ces references depuis toujours
        // (StsAuthoritativeCombatService) ; le duel, ou chaque duelliste apporte la relique de
        // base de son personnage, ne le faisait pas.
        Set<String> relicIds = new LinkedHashSet<>();
        for (CombatantState combatant : state.combatants()) {
            combatant.relics().stream()
                    .filter(relicId -> relicId != null && !relicId.isBlank())
                    .forEach(relicIds::add);
        }
        cardIds.addAll(relicEngine.generatedCardReferences(relicIds));
        return catalog(state.dataVersion(), cardIds, seats);
    }

    /**
     * @param seats les sieges de la bataille, dont les coups des boss sont verses au catalogue.
     *              Un boss ne tient ses coups dans aucune pile -- il les joue depuis son motif --
     *              donc les collecter depuis les piles ne les trouverait jamais, et le premier
     *              coup du boss serait refuse faute de definition.
     */
    private CardCatalog catalog(
            String dataVersion, java.util.Collection<String> cardReferences, List<StsPvpSeat> seats) {
        // No character scoping in PvP: the players may have picked different ones, and a
        // single combat cannot be scoped to all of them.
        List<StsPvpSeat> bosses = seats.stream().filter(StsPvpSeat::isBoss).toList();
        ArrayNode enemies = gameDataService.enemies(dataVersion);
        // A boss that starts with a follow-up naming no card plays one from the whole game.
        List<JsonNode> bossEnemies = bosses.stream()
                .map(boss -> EnemyPatternReader.findEnemy(enemies, boss.bossEnemyId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        CardCatalog base = cardCatalogFactory.create(
                gameDataService.cards(dataVersion), dataVersion, cardReferences, "", bossEnemies);

        if (bosses.isEmpty()) {
            return base;
        }

        Map<String, CardDefinition> merged = new LinkedHashMap<>(base.cards());
        for (StsPvpSeat boss : bosses) {
            JsonNode enemy = EnemyPatternReader.findEnemy(enemies, boss.bossEnemyId());
            for (CardDefinition definition :
                    EnemyPatternReader.moveDefinitions(base, enemy, boss.bossEnemyId(), enemyMoveParser)) {
                merged.put(definition.id(), definition);
            }
        }
        return new CardCatalog(base.dataVersion(), base.character(), merged);
    }

    private List<String> activeDeckCardIds(StsPvpSeat participant) {
        List<StsPvpDeck> decks = deckRepository
                .findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(
                        participant.keycloakSub(), participant.characterOrDefault());
        StsPvpDeck deck = decks.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .findFirst()
                // Falling back to the most recent deck keeps a player who never flagged one from
                // blocking a battle both players already agreed to.
                .orElseGet(() -> decks.stream().findFirst().orElse(null));
        if (deck == null) {
            throw new BadRequestException(
                    "Un joueur de ce combat PVP n'a pas de deck pour " + participant.characterOrDefault() + ".");
        }

        List<String> cardIds = new ArrayList<>();
        for (JsonNode cardId : deck.getCardIds() == null ? objectMapper.createArrayNode() : deck.getCardIds()) {
            String value = cardId.asText("").trim();
            if (!value.isBlank()) {
                cardIds.add(value);
            }
        }
        if (cardIds.isEmpty()) {
            throw new BadRequestException("Un joueur de ce combat PVP a un deck vide.");
        }
        return cardIds;
    }

    private Optional<StsPvpProfile> profile(StsPvpSeat participant) {
        return profileRepository.findByUser_KeycloakSub(participant.keycloakSub());
    }

    /**
     * Tous les sieges d'une bataille, dans l'ordre des equipes puis des places.
     *
     * <p>Cette methode lisait auparavant un seul participant par equipe — {@code team.get(0)} —
     * et se servait de l'indice d'equipe comme d'un indice de joueur. C'etait exact tant qu'une
     * bataille opposait un joueur a un autre, et c'etait la seule chose qui l'empechait d'en
     * opposer davantage : le second joueur d'une equipe n'existait tout simplement pas pour le
     * serveur, alors meme que la colonne {@code teams} est un tableau de tableaux depuis
     * toujours et que le moteur n'a jamais compte les joueurs.
     *
     * <p>Une bataille d'avant les modes ne porte, sur chaque participant, que son
     * {@code userId} : elle est donc lue comme deux sieges humains, un par equipe, ce qui est
     * exactement ce qu'elle etait.
     *
     * <p>Publique parce que tout ce qui sert une bataille a ses joueurs a besoin des memes
     * identites — l'identifiant d'utilisateur dont le combattant porte le nom, et le sub keycloak
     * sous lequel sa socket est connue — et relire le tableau {@code teams} deux fois est la
     * facon dont les deux bouts d'une bataille commencent a etre en desaccord sur qui joue ou.
     */
    public List<StsPvpSeat> seats(StsPvpBattle battle) {
        JsonNode teams = battle.getTeams();
        if (teams == null || !teams.isArray() || teams.size() != PVP_TEAM_COUNT) {
            throw new BadRequestException("Ce combat PVP n'oppose pas deux equipes.");
        }

        List<StsPvpSeat> seats = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < teams.size(); teamIndex++) {
            JsonNode team = teams.get(teamIndex);
            if (team == null || !team.isArray() || team.isEmpty()) {
                throw new BadRequestException("Une equipe de ce combat PVP est vide.");
            }
            for (JsonNode participant : team) {
                seats.add(seat(participant, teamIndex));
            }
        }
        return List.copyOf(seats);
    }

    /** Les sieges auxquels le serveur a quelque chose a envoyer : les joueurs. */
    public List<StsPvpSeat> playerSeats(StsPvpBattle battle) {
        return seats(battle).stream().filter(StsPvpSeat::isReachablePlayer).toList();
    }

    private StsPvpSeat seat(JsonNode participant, int teamIndex) {
        String character = participant.path("selectedCharacter").asText("");
        String displayName = participant.path("displayName").asText("");

        String bossEnemyId = participant.path("bossEnemyId").asText("");
        if (!bossEnemyId.isBlank()) {
            // Absent sur les raids ouverts avant que les rencontres ne règlent leurs PV : 1 garde
            // l'échelle d'alors.
            return StsPvpSeat.boss(
                    requireCombatantId(participant), displayName, bossEnemyId, teamIndex,
                    participant.path("hpMultiplier").asDouble(1d));
        }

        UUID deckOwner = uuidOrNull(participant.path("deckOwnerUserId").asText(null));
        if (deckOwner != null) {
            return StsPvpSeat.aiAlly(
                    requireCombatantId(participant), displayName, character, teamIndex, deckOwner);
        }

        UUID userId = uuidOrNull(participant.path("userId").asText(null));
        if (userId == null) {
            throw new BadRequestException("Un participant de ce combat PVP n'a pas d'identifiant.");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException(
                        "Un participant de ce combat PVP est introuvable."));
        return StsPvpSeat.human(
                userId,
                user.getKeycloakSub(),
                displayName,
                character.isBlank() ? "EP" : character,
                teamIndex);
    }

    /**
     * Un siege que le serveur pilote doit nommer son combattant : il n'a pas d'identifiant
     * d'utilisateur dont le combattant pourrait prendre le nom, et le deduire de sa place le
     * ferait changer de nom des qu'un siege bougerait.
     */
    private static String requireCombatantId(JsonNode participant) {
        String combatantId = participant.path("combatantId").asText("");
        if (combatantId.isBlank()) {
            throw new BadRequestException("Un combattant de ce combat PVP n'a pas d'identifiant.");
        }
        return combatantId;
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
