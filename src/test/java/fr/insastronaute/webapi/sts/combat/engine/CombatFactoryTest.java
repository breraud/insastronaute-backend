package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.EPRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GCRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MECARelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MRIERelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatFactoryTest {

    private static final RelicEngine RELIC_ENGINE = new RelicEngine(List.of(
            new EPRelicHandler(), new MECARelicHandler(), new GCRelicHandler(), new MRIERelicHandler()
    ));

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void createsTheSameStateAndEventsForTheSameSetup() {
        CombatSetup setup = setup(42L, hero(deck("hero", 10), 5), enemy(deck("enemy", 3), 8));

        CombatCreated first = factory().create(setup);
        CombatCreated second = factory().create(setup);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void usesTheCatalogVersionAndRejectsAMismatchedSetupVersion() {
        CombatSetup matching = setup(
                42L, hero(List.of(card("hero-card")), 0), enemy(List.of(), 1));
        CombatCreated created = factory().create(matching);
        CombatSetup mismatched = new CombatSetup(
                matching.combatId(), "other-version", matching.seed(), matching.combatants());

        assertThat(created.state().dataVersion()).isEqualTo("v1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory().create(mismatched))
                .withMessageContaining("dataVersion");
    }

    @Test
    void rejectsUnknownCardDefinitionsBeforeCreatingACombat() {
        CombatSetup setup = setup(
                42L,
                hero(List.of(new CardInstance("unknown-instance", "unknown-definition")), 0),
                enemy(List.of(), 1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory().create(setup))
                .withMessageContaining("unknown-definition")
                .withMessageContaining("hero");
    }

    @Test
    void initializesCombatAndDrawsFiveCardsFromTheFrontOfTheActiveDeck() {
        CombatCreated created = factory().create(
                setup(42L, hero(deck("hero", 10), 5), enemy(deck("enemy", 3), 8)));

        CombatState state = created.state();
        CombatantState active = combatant(state, "hero");
        CombatantState waiting = combatant(state, "enemy");
        PileShuffled heroShuffle = (PileShuffled) created.events().get(0);

        assertThat(state.revision()).isZero();
        assertThat(state.status().name()).isEqualTo("ACTIVE");
        assertThat(state.winnerTeamId()).isNull();
        assertThat(state.activeCombatantId()).isEqualTo("hero");
        assertThat(state.timeline()).containsExactly(
                new TimelineEntry("hero", 5, 0),
                new TimelineEntry("enemy", 8, 1));

        assertThat(active.hp()).isEqualTo(active.maxHp()).isEqualTo(80);
        assertThat(active.armor()).isZero();
        assertThat(active.energy()).isEqualTo(active.baseEnergy()).isEqualTo(3);
        assertThat(active.piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactlyElementsOf(heroShuffle.cardInstanceIds().subList(0, 5));
        assertThat(active.piles().draw())
                .extracting(CardInstance::instanceId)
                .containsExactlyElementsOf(heroShuffle.cardInstanceIds().subList(5, 10));
        assertThat(active.piles().discard()).isEmpty();
        assertThat(active.piles().exhaust()).isEmpty();

        assertThat(waiting.hp()).isEqualTo(waiting.maxHp()).isEqualTo(30);
        assertThat(waiting.armor()).isZero();
        assertThat(waiting.energy()).isZero();
        assertThat(waiting.piles().hand()).isEmpty();
        assertThat(waiting.piles().draw()).hasSize(3);
        assertThat(waiting.piles().discard()).isEmpty();
        assertThat(waiting.piles().exhaust()).isEmpty();

        assertThat(created.events()).hasSize(8);
        assertThat(created.events().subList(0, 2)).allMatch(PileShuffled.class::isInstance);
        assertThat(created.events().subList(2, 7)).allMatch(CardDrawn.class::isInstance);
        assertThat(created.events().get(7))
                .isEqualTo(new TurnStarted(COMBAT_ID, 0, "hero", 5, 3));
        assertThat(created.events().subList(2, 7))
                .containsExactly(
                        new CardDrawn(COMBAT_ID, 0, "hero", heroShuffle.cardInstanceIds().get(0), "strike", 0),
                        new CardDrawn(COMBAT_ID, 0, "hero", heroShuffle.cardInstanceIds().get(1), "strike", 1),
                        new CardDrawn(COMBAT_ID, 0, "hero", heroShuffle.cardInstanceIds().get(2), "strike", 2),
                        new CardDrawn(COMBAT_ID, 0, "hero", heroShuffle.cardInstanceIds().get(3), "strike", 3),
                        new CardDrawn(COMBAT_ID, 0, "hero", heroShuffle.cardInstanceIds().get(4), "strike", 4));
    }

    @Test
    void aDifferentSeedChangesTheShuffleOfALargeDeck() {
        CombatSetup seed42 = setup(42L, hero(deck("hero", 10), 0), enemy(List.of(), 1));
        CombatSetup seed43 = setup(43L, hero(deck("hero", 10), 0), enemy(List.of(), 1));

        PileShuffled first = firstShuffle(factory().create(seed42));
        PileShuffled second = firstShuffle(factory().create(seed43));

        assertThat(first.cardInstanceIds()).isNotEqualTo(second.cardInstanceIds());
    }

    @Test
    void timelineTiesKeepInputSequenceBeforeTheIdentifierFallback() {
        CombatCreated created = factory().create(
                setup(42L, heroNamed("zeta", List.of(), 5), enemyNamed("alpha", List.of(), 5)));

        assertThat(created.state().timeline()).containsExactly(
                new TimelineEntry("zeta", 5, 0),
                new TimelineEntry("alpha", 5, 1));
        assertThat(created.state().activeCombatantId()).isEqualTo("zeta");
    }

    @Test
    void skipsDeadTimelineEntriesWhenChoosingTheInitialActor() {
        CombatantSetup dead = new CombatantSetup(
                "dead", "players", ControllerType.HUMAN, 0, 3, 1, 0, List.of());
        CombatCreated created = factory().create(
                setup(42L,
                        dead,
                        heroNamed("alive-player", List.of(), 1),
                        enemyNamed("alive-enemy", List.of(), 2)));

        assertThat(created.state().activeCombatantId()).isEqualTo("alive-player");
        assertThat(combatant(created.state(), "dead").energy()).isZero();
        assertThat(combatant(created.state(), "alive-player").energy()).isEqualTo(3);
        assertThat(combatant(created.state(), "alive-enemy").energy()).isZero();
    }

    @Test
    void rejectsSetupWithOnlyOneLivingTeam() {
        CombatantSetup dead = new CombatantSetup(
                "dead", "players", ControllerType.HUMAN, 0, 3, 1, 0, List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> setup(42L, dead, enemyNamed("alive", List.of(), 1)))
                .withMessageContaining("living teams");
    }

    @Test
    void drawsEveryCardWhenTheActiveDeckContainsFewerThanFive() {
        CombatCreated created = factory().create(
                setup(42L, hero(deck("hero", 3), 0), enemy(List.of(), 1)));

        CombatantState hero = combatant(created.state(), "hero");

        assertThat(hero.piles().hand()).hasSize(3);
        assertThat(hero.piles().draw()).isEmpty();
        assertThat(created.events()).filteredOn(CardDrawn.class::isInstance).hasSize(3);
    }

    @Test
    void acceptsEmptyDecksAndDoesNotConsumeRandomnessForDecksOfSizeZeroOrOne() {
        CombatCreated created = factory().create(
                setup(42L, hero(List.of(), 0), enemy(deck("enemy", 1), 1)));

        assertThat(created.state().random()).isEqualTo(new RandomState(42L));
        assertThat(created.events()).noneMatch(PileShuffled.class::isInstance);
    }

    @Test
    void chainsRandomConsumptionAcrossCombatantDecksInSetupOrder() {
        long seed = 42L;
        CombatCreated created = factory().create(
                setup(seed, hero(deck("hero", 4), 0), enemy(deck("enemy", 3), 1)));

        RandomState expectedRandom = new RandomState(seed);
        for (int bound : List.of(4, 3, 2, 3, 2)) {
            expectedRandom = expectedRandom.nextInt(bound).next();
        }

        assertThat(created.state().random()).isEqualTo(expectedRandom);
    }

    @Test
    void rejectsDuplicateCombatantAndCardInstanceIdentifiers() {
        CombatantSetup duplicateHero = heroNamed("hero", List.of(card("other")), 1);
        CombatantSetup duplicateCard = enemy(List.of(card("shared")), 1);

        assertThatIllegalArgumentException().isThrownBy(
                () -> factory().create(setup(42L, hero(List.of(), 0), duplicateHero)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> factory().create(
                        setup(42L, hero(List.of(card("shared")), 0), duplicateCard)));
    }

    @Test
    void rejectsInvalidSetups() {
        CombatantSetup hero = hero(List.of(), 0);

        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatSetup(COMBAT_ID, "v1", 42L,
                        List.of(hero, heroNamed("ally", List.of(), 1))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        -1, 3, 1, 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        80, -1, 1, 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        80, 3, 0, 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        80, 3, 1, -1, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> factory().create(
                        setup(42L,
                                new CombatantSetup("dead-a", "a", ControllerType.HUMAN,
                                        0, 3, 1, 0, List.of()),
                                new CombatantSetup("dead-b", "b", ControllerType.AI,
                                        0, 2, 1, 0, List.of()))));
        assertThatNullPointerException().isThrownBy(() -> factory().create(null));
        assertThatNullPointerException().isThrownBy(() -> new CombatFactory(null, RELIC_ENGINE));
        assertThatNullPointerException().isThrownBy(() -> new CombatFactory(new CardCatalog("v1", Map.of()), null));
    }

    @Test
    void setupAndEventsDefensivelyCopyLists() {
        List<CardInstance> deck = new ArrayList<>(List.of(card("one"), card("two")));
        CombatantSetup hero = hero(deck, 0);
        List<CombatantSetup> combatants = new ArrayList<>(List.of(hero, enemy(List.of(), 1)));
        CombatSetup setup = new CombatSetup(COMBAT_ID, "v1", 42L, combatants);
        List<String> shuffledIds = new ArrayList<>(List.of("one", "two"));
        PileShuffled shuffled = new PileShuffled(COMBAT_ID, 0, "hero", "DRAW", shuffledIds);
        List<CombatEvent> events = new ArrayList<>(List.of(shuffled));
        CombatCreated created = new CombatCreated(
                factory().create(setup).state(), events);

        deck.clear();
        combatants.clear();
        shuffledIds.clear();
        events.clear();

        assertThat(hero.deck()).hasSize(2);
        assertThat(setup.combatants()).hasSize(2);
        assertThat(shuffled.cardInstanceIds()).containsExactly("one", "two");
        assertThat(created.events()).containsExactly(shuffled);
        assertThatThrownBy(() -> hero.deck().add(card("three")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> setup.combatants().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> shuffled.cardInstanceIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> created.events().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesRelicsOnCombatants() {
        CombatantSetup hero = new CombatantSetup(
                "hero", "players", ControllerType.HUMAN, 80, 3, 10, 0,
                List.of(card("card-1")), List.of("custom_relic"));
        CombatantSetup enemy = enemy(List.of(), 1);
        CombatSetup setup = setup(42L, hero, enemy);

        CombatCreated created = factory().create(setup);

        assertThat(combatant(created.state(), "hero").relics()).containsExactly("custom_relic");
    }

    @Test
    void opensCombatantsOnTheirStartingHpAndLeavesFullHealthOnesUntouched() {
        CombatantSetup wounded = new CombatantSetup(
                "hero", "players", ControllerType.HUMAN, 80, 12, 3, 10, 0,
                List.of(card("card-1")), List.of(), List.of(), 0);

        CombatState state = factory().create(setup(42L, wounded, enemy(List.of(), 1))).state();

        assertThat(combatant(state, "hero").maxHp()).isEqualTo(80);
        assertThat(combatant(state, "hero").hp()).isEqualTo(12);
        assertThat(combatant(state, "enemy").maxHp()).isEqualTo(30);
        assertThat(combatant(state, "enemy").hp()).isEqualTo(30);
    }

    @Test
    void skipsCombatantsThatOpenTheCombatDeadWhenChoosingTheInitialActor() {
        CombatantSetup deadOnArrival = new CombatantSetup(
                "fallen", "players", ControllerType.HUMAN, 80, 0, 3, 10, 0,
                List.of(), List.of(), List.of(), 0);

        CombatCreated created = factory().create(
                setup(42L,
                        deadOnArrival,
                        heroNamed("alive-player", List.of(), 1),
                        enemyNamed("alive-enemy", List.of(), 2)));

        assertThat(created.state().activeCombatantId()).isEqualTo("alive-player");
        assertThat(combatant(created.state(), "fallen").hp()).isZero();
        assertThat(combatant(created.state(), "fallen").energy()).isZero();
    }

    @Test
    void rejectsAStartingHpThatIsNegativeOrAboveMaxHp() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        80, -1, 3, 10, 0, List.of(), List.of(), List.of(), 0))
                .withMessageContaining("startingHp");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CombatantSetup("hero", "players", ControllerType.HUMAN,
                        80, 81, 3, 10, 0, List.of(), List.of(), List.of(), 0))
                .withMessageContaining("startingHp");
    }

    @Test
    void rejectsSetupWhereOnlyOneTeamOpensTheCombatAlive() {
        CombatantSetup deadOnArrival = new CombatantSetup(
                "fallen", "players", ControllerType.HUMAN, 80, 0, 3, 10, 0,
                List.of(), List.of(), List.of(), 0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> setup(42L, deadOnArrival, enemyNamed("alive", List.of(), 1)))
                .withMessageContaining("living teams");
    }

    /**
     * A combat that is over before its first turn is refused, wherever the decision came from.
     *
     * <p>The setup answers for the health it is handed, and refuses a side that walks in already
     * dead. Relics run after that answer and can move health, so they can undo it — no shipped
     * onCombatStart touches health today, which is exactly what was said of the end-of-turn
     * statuses right up until one of them stopped a player's combat for good. The refusal has to
     * name what it caught, too: left to the state's own invariant, the only thing said was that
     * an active combat needs two living teams, which describes the wreck and not the relic.
     */
    @Test
    void refusesACombatARelicHasAlreadyDecidedBeforeTheFirstTurn() {
        CombatFactory factory = new CombatFactory(
                new CardCatalog("v1", Map.of()),
                new RelicEngine(List.of(new FatalOnCombatStart())));
        CombatSetup setup = setup(
                42L,
                new CombatantSetup(
                        "hero", "players", ControllerType.HUMAN, 80, 80, 3, 10, 0,
                        List.of(), List.of("fatal"), List.of(), 0),
                enemy(List.of(), 1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(setup))
                .withMessageContaining("relic")
                .withMessageContaining("living teams");
    }

    /** A combat the relics leave alone is still created. */
    @Test
    void createsTheCombatWhenTheRelicsLeaveBothSidesStanding() {
        CombatFactory factory = new CombatFactory(
                new CardCatalog("v1", Map.of()),
                new RelicEngine(List.of(new FatalOnCombatStart())));

        CombatCreated created = factory.create(
                setup(42L, heroNamed("hero", List.of(), 0), enemy(List.of(), 1)));

        assertThat(created.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(created.state().activeCombatantId()).isEqualTo("hero");
    }

    /** Kills whoever carries it as the combat opens, which no shipped relic does. */
    private static final class FatalOnCombatStart implements RelicHandler {

        @Override
        public String id() {
            return "fatal";
        }

        @Override
        public CombatantState onCombatStart(
                CombatState state,
                CombatantState owner,
                List<CombatEvent> events
        ) {
            return new CombatantState(
                    owner.combatantId(), owner.teamId(), owner.controllerType(), owner.maxHp(),
                    0, owner.armor(), owner.energy(), owner.baseEnergy(), owner.turnDelayTicks(),
                    owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
                    owner.patternIndex(), owner.relicMemory());
        }
    }

    private static CombatSetup setup(long seed, CombatantSetup... combatants) {
        return new CombatSetup(COMBAT_ID, "v1", seed, List.of(combatants));
    }

    private static CombatFactory factory() {
        CardDefinition strike = new CardDefinition(
                "strike",
                1,
                TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 1)));
        return new CombatFactory(new CardCatalog("v1", Map.of("strike", strike)), RELIC_ENGINE);
    }

    private static CombatantSetup hero(List<CardInstance> deck, long readyAtTick) {
        return heroNamed("hero", deck, readyAtTick);
    }

    private static CombatantSetup heroNamed(
            String combatantId,
            List<CardInstance> deck,
            long readyAtTick
    ) {
        return new CombatantSetup(
                combatantId, "players", ControllerType.HUMAN, 80, 3, 10, readyAtTick, deck);
    }

    private static CombatantSetup enemy(List<CardInstance> deck, long readyAtTick) {
        return enemyNamed("enemy", deck, readyAtTick);
    }

    private static CombatantSetup enemyNamed(
            String combatantId,
            List<CardInstance> deck,
            long readyAtTick
    ) {
        return new CombatantSetup(
                combatantId, "enemies", ControllerType.AI, 30, 2, 12, readyAtTick, deck);
    }

    private static List<CardInstance> deck(String prefix, int size) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            deck.add(card(prefix + "-" + index));
        }
        return deck;
    }

    private static CardInstance card(String instanceId) {
        return new CardInstance(instanceId, "strike");
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static PileShuffled firstShuffle(CombatCreated created) {
        return created.events().stream()
                .filter(PileShuffled.class::isInstance)
                .map(PileShuffled.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
