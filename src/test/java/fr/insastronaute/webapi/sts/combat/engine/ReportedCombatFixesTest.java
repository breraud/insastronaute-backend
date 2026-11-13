package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.effect.GainEnergyHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusEvolution;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The combat bugs reported together in September 2026, each pinned where it used to fail.
 */
class ReportedCombatFixesTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000915");
    private static final String HERO = "hero";
    private static final String FOE = "foe";
    private static final String OTHER_FOE = "other-foe";

    // ---- Milieu continu ----------------------------------------------------------------

    /**
     * A relic that has nothing to say about a blow used to answer with its owner alone, and
     * Continuous -- a status striking its own holder -- took that answer for the whole table:
     * the other side vanished and the combat ended on the spot, won by nobody.
     */
    @Test
    void continuousOnARelicBearerLeavesTheCombatRunning() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Continuous", 3, 5, "", 0)), List.of("inert"),
                List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());
        CombatState state = state(List.of(hero, foe), HERO);
        CombatEngine engine = new CombatEngine(catalog(), inertRelics());

        CombatResult result = engine.handle(state, new EndTurnCommand(COMBAT_ID, 1, HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().combatants()).extracting(CombatantState::combatantId)
                .containsExactly(HERO, FOE);
        assertThat(find(result.state().combatants(), HERO).hp()).isEqualTo(27);
    }

    @Test
    void aRelicWithNothingToSayKeepsTheWholeTableInTheStep() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 27,
                List.of(), List.of("inert"), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        ResolutionStep step = inertRelics().processDamageTaken(hero, 3, List.of(hero, foe),
                timeline(HERO, FOE), new RandomState(1), COMBAT_ID, 1, new ArrayList<>());

        assertThat(step.combatants()).extracting(CombatantState::combatantId)
                .containsExactly(HERO, FOE);
    }

    // ---- Plein régime ------------------------------------------------------------------

    /**
     * Losing HP during your own turn grants the Strength at once, so the rest of the same card
     * already hits harder.
     */
    @Test
    void fullThrottleGrantsStrengthWhenItsHolderLosesHpOnItsOwnTurn() {
        CardDefinition bleed = new CardDefinition("bleed", 0, CardType.SKILL,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(
                        new EffectDefinition(EffectType.LOSE_HP, 2, true),
                        new EffectDefinition(EffectType.DAMAGE, 5, false)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("FullThrottle", 1, -1, "", 0)), List.of(),
                List.of(new CardInstance("bleed-1", "bleed")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = play(catalog(bleed), state(List.of(hero, foe), HERO), "bleed-1", FOE);

        assertThat(result.accepted()).isTrue();
        assertThat(strengthOf(find(result.state().combatants(), HERO))).isEqualTo(1);
        assertThat(find(result.state().combatants(), FOE).hp())
                .as("the Strength already counts for the blow that follows")
                .isEqualTo(14);
    }

    @Test
    void fullThrottleStaysQuietWhenItsHolderIsStruckOnSomebodyElsesTurn() {
        CardDefinition strike = attack("strike", 4);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("FullThrottle", 1, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(strike), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(FOE, HERO),
                        new RandomState(1), FOE, List.of(HERO), strike, List.of());

        assertThat(find(resolution.combatants(), HERO).hp()).isEqualTo(26);
        assertThat(strengthOf(find(resolution.combatants(), HERO))).isZero();
    }

    // ---- Sadisme -----------------------------------------------------------------------

    @Test
    void sadismGrowsAsItsAdversariesWeaken() {
        assertThat(stageAgainst(17)).as("85%%").isEqualTo("Sadism");
        assertThat(stageAgainst(16)).as("exactly 80%%").isEqualTo("SadismII");
        assertThat(stageAgainst(10)).as("under half, both stages at once").isEqualTo("SadismIII");
    }

    @Test
    void aBlowThatCrossesTheThresholdLeavesTheRevisionWithTheNextStage() {
        CardDefinition strike = attack("strike", 5);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Sadism", 0, -1, "", 0)), List.of(),
                List.of(new CardInstance("strike-1", "strike")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = play(catalog(strike), state(List.of(hero, foe), HERO), "strike-1", FOE);

        assertThat(find(result.state().combatants(), HERO).statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("SadismII");
        assertThat(result.events()).filteredOn(StatusApplied.class::isInstance)
                .extracting(event -> ((StatusApplied) event).statusType())
                .contains("SadismII");
    }

    @Test
    void sadismTwoAlsoStrengthensTheArmourItsHolderGains() {
        CardDefinition shield = new CardDefinition("shield", 0, CardType.SKILL, TargetingMode.SELF,
                Set.of(), List.of(new EffectDefinition(EffectType.ARMOR, 10, true)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("SadismII", 0, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(shield), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                        new RandomState(1), HERO, List.of(HERO), shield, List.of());

        assertThat(find(resolution.combatants(), HERO).armor()).isEqualTo(12);
    }

    @Test
    void sadismThreeAnswersAnAttackOnAWeakenedAdversaryWithAnother() {
        CardDefinition strike = attack("strike", 1);
        CardDefinition relentless = new CardDefinition("acharnement", "Acharnement", "", 0, false,
                CardType.ATTACK, TargetingMode.ALL_ENEMIES,
                Set.of(CardTag.FOLLOW_UP, CardTag.EXHAUST), List.of(), List.of(
                        new EffectDefinition(EffectType.DAMAGE, 1),
                        new EffectDefinition(EffectType.DELAY_TURN, 4)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("SadismIII", 0, -1, "", 0)), List.of(),
                List.of(new CardInstance("strike-1", "strike")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 40, 10, List.of(),
                List.of(), List.of());

        CombatResult result = play(catalog(strike, relentless), state(List.of(hero, foe), HERO),
                "strike-1", FOE);

        assertThat(result.events()).filteredOn(CardPlayed.class::isInstance)
                .extracting(event -> ((CardPlayed) event).definitionId())
                .containsExactly("strike", "acharnement");
        assertThat(find(result.state().combatants(), FOE).hp()).isEqualTo(8);
    }

    // ---- Pavillon ----------------------------------------------------------------------

    @Test
    void pavillonRewardsDispellingAFramedBuff() {
        assertThat(strengthAfterDispelling(new StatusInstance("Accelerate", 1, -1, "", 0)))
                .as("framed by its kind").isEqualTo(1);
        assertThat(strengthAfterDispelling(
                new StatusInstance("Strength", 2, -1, "", 0, false, null, 0)))
                .as("framed by a card").isEqualTo(1);
        assertThat(strengthAfterDispelling(new StatusInstance("Strength", 2, -1, "", 0)))
                .as("an ordinary buff").isZero();
    }

    /** Any framed status a card takes away counts, debuffs included. */
    @Test
    void pavillonRewardsDispellingAFramedDebuffToo() {
        CardDefinition cleanse = new CardDefinition("cleanse", 0, CardType.SKILL,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(new EffectDefinition(
                        EffectType.DISPEL_DEBUFF, 1, false, null, 0, null, null, null, null,
                        List.of(), false, true, null)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Pavillon", 0, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20,
                List.of(new StatusInstance("Compilation", 5, -1, "", 0)), List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(cleanse), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                        new RandomState(1), HERO, List.of(FOE), cleanse, List.of());

        assertThat(find(resolution.combatants(), FOE).statuses()).isEmpty();
        assertThat(strengthOf(find(resolution.combatants(), HERO))).isEqualTo(1);
    }

    // ---- Ce qu'on glisse dans une pile -------------------------------------------------

    /**
     * A card handed to a pile lands anywhere in it. Appended, it sat under everything: a card
     * given to the draw pile only came up once the whole pile had gone by.
     */
    @Test
    void aCardGivenToThePilesLandsSomewhereInsideThem() {
        CardDefinition curse = new CardDefinition("gift", 0, CardType.SKILL, TargetingMode.SELF,
                Set.of(), List.of(
                        new EffectDefinition(EffectType.ADD_CARD_TO_DRAW_PILE, 1, true, null, 0,
                                null, "strike"),
                        new EffectDefinition(EffectType.ADD_CARD_TO_DISCARD_PILE, 1, true, null, 0,
                                null, "strike")));
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            deck.add(new CardInstance("deck-" + index, "strike"));
        }
        CombatantState hero = new CombatantState(HERO, "players", ControllerType.HUMAN, 30, 30, 0,
                3, 3, 10, new CardPiles(deck, List.of(), deck, List.of()), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        boolean drawInside = false;
        boolean discardInside = false;
        for (int seed = 1; seed <= 12 && !(drawInside && discardInside); seed++) {
            EffectResolver.Resolution resolution =
                    new EffectResolver(catalog(curse, attack("strike", 1)), inertRelics())
                            .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                                    new RandomState(seed), HERO, List.of(HERO), curse, List.of());
            CardPiles piles = find(resolution.combatants(), HERO).piles();
            assertThat(piles.draw()).hasSize(9);
            assertThat(piles.discard()).hasSize(9);
            drawInside = drawInside || !giftIsAtTheBottom(piles.draw());
            discardInside = discardInside || !giftIsAtTheBottom(piles.discard());
        }
        assertThat(drawInside).as("the draw pile gift is not always at the bottom").isTrue();
        assertThat(discardInside).as("the discard pile gift is not always at the bottom").isTrue();
    }

    // ---- Suivis aléatoires -------------------------------------------------------------

    /**
     * A follow-up naming no card plays one drawn from the whole game, like Unity's
     * GetRandomCard: not from the deck, and not only among cards an effect may hand out.
     */
    @Test
    void aFollowUpNamingNoCardDrawsFromTheWholeGame() {
        CardDefinition strike = attack("strike", 1);
        CardDefinition special = new CardDefinition("payload", "Payload", "Impossible", 0, false,
                CardType.SKILL, TargetingMode.SELF, Set.of(CardTag.FOLLOW_UP, CardTag.UNOBTAINABLE),
                List.of(), List.of(new EffectDefinition(EffectType.ARMOR, 3, true)),
                fr.insastronaute.webapi.sts.combat.catalog.CardRarity.SPECIAL);
        CardDefinition starter = new CardDefinition("starter", "Starter", "Starting", 0, false,
                CardType.SKILL, TargetingMode.SELF, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
        CardCatalog catalog = catalog(strike, special, starter);

        assertThat(catalog.followUpPool()).extracting(CardDefinition::id)
                .describedAs("everything but the Starting cards, whatever its tags or rarity")
                .containsExactlyInAnyOrder("strike", "payload");
        assertThat(playedAfterFollowUp(catalog, "")).isIn("strike", "payload");
    }

    /** A name no card carries is played as a random card, not skipped. */
    @Test
    void aFollowUpNamingAnUnknownCardStillPlaysOne() {
        CardCatalog catalog = catalog(attack("strike", 1));

        assertThat(playedAfterFollowUp(catalog, "Pulse")).isEqualTo("strike");
    }

    @Test
    void theCombatCatalogHoldsTheWholeGameWhenARandomFollowUpIsReachable() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode data = mapper.createObjectNode();
        data.put("dataVersion", "v1");
        com.fasterxml.jackson.databind.node.ArrayNode cards = data.putArray("cards");
        com.fasterxml.jackson.databind.node.ObjectNode coloriage = playableCard(cards, "coloriage");
        com.fasterxml.jackson.databind.node.ObjectNode followUp = coloriage.withArray("effects").addObject();
        followUp.put("type", "Status");
        followUp.put("statusType", "CardFollowUp");
        followUp.put("value", 0);
        followUp.put("duration", 1);
        followUp.put("cardID", "");
        playableCard(cards, "elsewhere");
        playableCard(cards, "ordinary");

        CardCatalog fromDeck = new fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory()
                .create(data, "v1", List.of("coloriage"), "CFI");
        CardCatalog plainDeck = new fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory()
                .create(data, "v1", List.of("ordinary"), "CFI");
        com.fasterxml.jackson.databind.node.ObjectNode riftAdept = mapper.createObjectNode();
        riftAdept.put("startingStatus", "CardFollowUp");
        riftAdept.put("startingStatusInfo", "Pulse");
        CardCatalog fromEnemy = new fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory()
                .create(data, "v1", List.of("ordinary"), "CFI", List.of(riftAdept));

        assertThat(fromDeck.find("elsewhere")).as("a deck card's random follow-up").isNotNull();
        assertThat(fromEnemy.find("elsewhere")).as("an enemy's unknown follow-up").isNotNull();
        assertThat(plainDeck.find("elsewhere")).as("nothing random reachable").isNull();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode playableCard(
            com.fasterxml.jackson.databind.node.ArrayNode cards, String id) {
        com.fasterxml.jackson.databind.node.ObjectNode card = cards.addObject();
        card.put("id", id);
        card.put("cost", 0);
        card.put("type", "Compétence");
        card.put("targetingMode", "Player");
        card.putArray("effects");
        return card;
    }

    /** Plays a card that sets up a follow-up firing on the very next card, and names what fired. */
    private static String playedAfterFollowUp(CardCatalog base, String followUpCardId) {
        Map<String, CardDefinition> cards = new LinkedHashMap<>(base.cards());
        CardDefinition filler = new CardDefinition("filler", "Filler", "Starting", 0, false,
                CardType.SKILL, TargetingMode.SELF, Set.of(), List.of(), List.of());
        cards.put(filler.id(), filler);
        CardCatalog catalog = new CardCatalog("v1", cards);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("CardFollowUp", 1, -1, followUpCardId, 0)), List.of(),
                List.of(new CardInstance("filler-1", "filler")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = play(catalog, state(List.of(hero, foe), HERO), "filler-1", HERO);

        assertThat(result.accepted()).isTrue();
        List<String> played = playedCards(result);
        assertThat(played).hasSize(2).first().isEqualTo("filler");
        return played.get(1);
    }

    // ---- Pleine lune --------------------------------------------------------------------

    /**
     * Pleine lune is FIXED in the catalog, so the status a card applies carries a value of 0: it
     * used to grant a Strength of 0 and a loss of Strength that took nothing back.
     */
    @Test
    void fullMoonAsACardAppliesItGrantsOneStrengthUntilTheTurnCloses() {
        StatusInstance fullMoon = fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog
                .instantiate("FullMoon", 1, 0, "pleine_lune", 0);
        CardDefinition strike = attack("strike", 3);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(fullMoon), List.of(), List.of(
                        new CardInstance("strike-1", "strike"), new CardInstance("strike-2", "strike")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 40, 40, List.of(),
                List.of(), List.of());
        CardCatalog catalog = catalog(strike);

        CombatResult first = play(catalog, state(List.of(hero, foe), HERO), "strike-1", FOE);
        assertThat(strengthOf(find(first.state().combatants(), HERO))).isEqualTo(1);
        CombatResult second = new CombatEngine(catalog, inertRelics()).handle(first.state(),
                new PlayCardCommand(COMBAT_ID, first.state().revision(), HERO, "strike-2",
                        List.of(FOE), List.of()));
        CombatantState moonlit = find(second.state().combatants(), HERO);

        assertThat(strengthOf(moonlit)).isEqualTo(2);
        assertThat(moonlit.statuses()).filteredOn(status -> status.statusType().equals("FullMoonTemp"))
                .singleElement()
                .satisfies(temp -> {
                    assertThat(temp.value()).as("both losses add up").isEqualTo(2);
                    assertThat(temp.duration()).as("and still end with this turn").isEqualTo(1);
                });

        CombatResult ended = new CombatEngine(catalog, inertRelics()).handle(second.state(),
                new EndTurnCommand(COMBAT_ID, second.state().revision(), HERO));
        assertThat(strengthOf(find(ended.state().combatants(), HERO))).isZero();
    }

    // ---- Sape --------------------------------------------------------------------------

    @Test
    void sapStacksLikeATrap() {
        List<StatusInstance> statuses = new ArrayList<>();
        fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.insert(
                statuses, new StatusInstance("Sap", 0, -1, "", 0));
        fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.insert(
                statuses, new StatusInstance("Sap", 0, -1, "", 0));

        assertThat(statuses).hasSize(2);
    }

    /** Every card played, by anyone, bites every Sap on the table once. */
    @Test
    void sapBitesEveryHolderOncePerInstanceWheneverAnyonePlaysACard() {
        CardDefinition calm = new CardDefinition("calm", 0, CardType.SKILL, TargetingMode.SELF,
                Set.of(), List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Sap", 0, -1, "", 0)), List.of(),
                List.of(new CardInstance("calm-1", "calm")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(
                new StatusInstance("Sap", 0, -1, "", 0), new StatusInstance("Sap", 0, -1, "", 0)),
                List.of(), List.of());

        CombatResult result = play(catalog(calm), state(List.of(hero, foe), HERO), "calm-1", HERO);

        assertThat(result.accepted()).isTrue();
        assertThat(find(result.state().combatants(), HERO).hp()).isEqualTo(29);
        assertThat(find(result.state().combatants(), HERO).armor())
                .as("the armour the card gave does not soak it").isEqualTo(1);
        assertThat(find(result.state().combatants(), FOE).hp()).isEqualTo(18);
        assertThat(result.events())
                .filteredOn(fr.insastronaute.webapi.sts.combat.event.HpLost.class::isInstance)
                .hasSize(2);
    }

    @Test
    void sapAlsoBitesWhenAnEnemyPlaysAMove() {
        CardDefinition strike = attack("strike", 2);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Sap", 0, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());
        CombatState state = state(List.of(foe, hero), FOE);

        CombatResult result = new CombatEngine(catalog(strike), inertRelics()).handle(state,
                new fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), FOE, "strike", List.of(HERO), 0));

        assertThat(result.accepted()).isTrue();
        assertThat(find(result.state().combatants(), HERO).hp())
                .as("2 from the blow, 1 from the Sap")
                .isEqualTo(27);
    }

    @Test
    void sapNoLongerBitesAsTheTurnOpens() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(),
                List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20,
                List.of(new StatusInstance("Sap", 0, -1, "", 0)), List.of(), List.of());
        CombatState state = state(List.of(hero, foe), HERO);

        CombatResult result = new CombatEngine(catalog(), inertRelics())
                .handle(state, new EndTurnCommand(COMBAT_ID, 1, HERO));

        assertThat(find(result.state().combatants(), FOE).hp()).isEqualTo(20);
    }

    // ---- Épines et Milieu continu : ce qui mord doit être annoncé -----------------------

    /**
     * The relics answer a blow on a copy of its events, and what Thorns wrote afterwards never
     * reached the client: the attacker's HP fell silently and the next hit looked stronger.
     */
    @Test
    void thornsAnnounceTheBiteTheyDeal() {
        CardDefinition strike = attack("strike", 5);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Thorns", 3, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(strike), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(FOE, HERO),
                        new RandomState(1), FOE, List.of(HERO), strike, List.of());

        assertThat(find(resolution.combatants(), FOE).hp()).isEqualTo(17);
        assertThat(resolution.events())
                .filteredOn(fr.insastronaute.webapi.sts.combat.event.HpLost.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.HpLost.class::cast)
                .singleElement()
                .satisfies(bite -> {
                    assertThat(bite.targetId()).isEqualTo(FOE);
                    assertThat(bite.actualLoss()).isEqualTo(3);
                });
    }

    @Test
    void continuousAnnouncesTheBlowItDeals() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Continuous", 1, 5, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = new CombatEngine(catalog(), inertRelics())
                .handle(state(List.of(hero, foe), HERO), new EndTurnCommand(COMBAT_ID, 1, HERO));

        assertThat(find(result.state().combatants(), HERO).hp()).isEqualTo(29);
        assertThat(result.events())
                .filteredOn(fr.insastronaute.webapi.sts.combat.event.DamageApplied.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.DamageApplied.class::cast)
                .singleElement()
                .satisfies(blow -> {
                    assertThat(blow.targetId()).isEqualTo(HERO);
                    assertThat(blow.hpLost()).isEqualTo(1);
                });
    }

    // ---- Suivis des ennemis ------------------------------------------------------------

    /**
     * The card an enemy's starting follow-up names was in no deck and no move, so it was missing
     * from the combat catalog: the follow-up treated it as unknown and played a random card,
     * mostly one of the player's own.
     */
    @Test
    void theCardAnEnemyFollowUpNamesIsInTheCombatCatalog() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode data = mapper.createObjectNode();
        data.put("dataVersion", "v1");
        com.fasterxml.jackson.databind.node.ArrayNode cards = data.putArray("cards");
        playableCard(cards, "baba").put("cardName", "Baba");
        playableCard(cards, "ordinary");
        playableCard(cards, "elsewhere");
        com.fasterxml.jackson.databind.node.ObjectNode enemy = mapper.createObjectNode();
        enemy.put("startingStatus", "CardFollowUp");
        enemy.put("startingStatusInfo", "Baba");

        CardCatalog catalog = new fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory()
                .create(data, "v1", List.of("ordinary"), "CFI", List.of(enemy));

        assertThat(catalog.find("Baba")).isNotNull();
        assertThat(catalog.find("elsewhere"))
                .as("a named card is not a random follow-up: the whole game stays out")
                .isNull();
    }

    // ---- Enchantements -----------------------------------------------------------------

    /**
     * Infini rejoue la carte en la rendant à la main pour 1 d'énergie. Une carte à coût X vide
     * déjà l'énergie en se jouant, si bien que la copie rendue ne fait jamais rien : elle ne se
     * voit donc plus proposer l'enchantement.
     */
    @Test
    void anXCostCardIsNeverOfferedInfinity() {
        CardDefinition ordinary = new CardDefinition("spend", "Spend", "", 1, false, CardType.SKILL,
                TargetingMode.SELF, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 3, true)));
        CardDefinition drain = new CardDefinition("drain", "Drain", "", 0, true, CardType.SKILL,
                TargetingMode.SELF, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 3, true)));

        assertThat(offeredTo(ordinary))
                .as("an ordinary card still may")
                .contains(fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog.INFINITY);
        assertThat(offeredTo(drain))
                .doesNotContain(fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog.INFINITY);
    }

    private static List<String> offeredTo(CardDefinition definition) {
        return fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog
                .candidatesFor(new CardInstance("copy-1", definition.id()), definition).stream()
                .map(fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog.Candidate::name)
                .toList();
    }

    /**
     * Mécanique : « S'active automatiquement à la fin du tour ». Son handler est vide et rien ne
     * lisait l'enchantement, si bien qu'une carte mécanique partait à la défausse sans se jouer.
     */
    @Test
    void aMechanicalCardPlaysItselfAsTheTurnCloses() {
        CardDefinition strike = attack("strike", 4);
        CardInstance mechanical = new CardInstance("strike-1", "strike",
                List.of(new fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance(
                        "Mécanique", 1)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(),
                List.of(), List.of(mechanical));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = new CombatEngine(catalog(strike), inertRelics())
                .handle(state(List.of(hero, foe), HERO), new EndTurnCommand(COMBAT_ID, 1, HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(find(result.state().combatants(), FOE).hp())
                .as("it played itself, free, on its way out")
                .isEqualTo(16);
        assertThat(playedCards(result)).containsExactly("strike");
        CombatantState closed = find(result.state().combatants(), HERO);
        assertThat(closed.piles().hand()).isEmpty();
        assertThat(closed.piles().discard()).extracting(CardInstance::instanceId)
                .containsExactly("strike-1");
        assertThat(closed.energy()).as("nothing was paid for it").isEqualTo(3);
    }

    @Test
    void aCardWithoutItIsStillJustDiscarded() {
        CardDefinition strike = attack("strike", 4);
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(),
                List.of(), List.of(new CardInstance("strike-1", "strike")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        CombatResult result = new CombatEngine(catalog(strike), inertRelics())
                .handle(state(List.of(hero, foe), HERO), new EndTurnCommand(COMBAT_ID, 1, HERO));

        assertThat(find(result.state().combatants(), FOE).hp()).isEqualTo(20);
        assertThat(playedCards(result)).isEmpty();
        assertThat(find(result.state().combatants(), HERO).piles().discard())
                .extracting(CardInstance::instanceId).containsExactly("strike-1");
    }

    // ---- Personne ne quitte la table ---------------------------------------------------

    /**
     * A step that hands back only the combatants it touched used to end the combat: the enemies
     * it forgot were no longer standing, their timeline entries went with them, and the client
     * closed a fight in which nobody had died.
     */
    @Test
    void aRevisionNeverDropsACombatantFromTheTable() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(),
                List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());
        CombatState previous = state(List.of(hero, foe), HERO);

        CombatResult result = CombatEngine.transition(previous, 2, previous.random(),
                List.of(hero), List.of(previous.timeline().getFirst()), HERO, new ArrayList<>(),
                previous.counters());

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().combatants()).extracting(CombatantState::combatantId)
                .containsExactly(HERO, FOE);
        assertThat(result.state().timeline()).extracting(TimelineEntry::combatantId)
                .containsExactly(HERO, FOE);
    }

    // ---- Désespoir ---------------------------------------------------------------------

    /**
     * Nothing left to draw and nothing left to play: the deck hands over a Désespoir rather than
     * leaving the player looking at an empty hand (DeckManager.Draw).
     */
    @Test
    void anEmptyDeckHandsOverADespairCard() {
        PileOperations.DrawResult result = PileOperations.draw(
                new CardPiles(List.of(), List.of(), List.of(), List.of()), 5, new RandomState(4),
                PileOperations.DESPAIR_CARD_ID);

        assertThat(result.drawn()).extracting(CardInstance::definitionId)
                .describedAs("one card, not one per card the player could not draw")
                .containsExactly(PileOperations.DESPAIR_CARD_ID);
        assertThat(result.piles().hand()).hasSize(1);
    }

    @Test
    void aPlayerWhoStillHoldsCardsIsLeftAlone() {
        CardPiles piles = new CardPiles(List.of(), List.of(
                new CardInstance("held-1", "strike"), new CardInstance("held-2", "strike")),
                List.of(), List.of());

        PileOperations.DrawResult result = PileOperations.draw(
                piles, 3, new RandomState(4), PileOperations.DESPAIR_CARD_ID);

        assertThat(result.drawn()).isEmpty();
        assertThat(result.piles().hand()).hasSize(2);
    }

    /** A combat whose data has no Désespoir is left with an empty hand rather than a card
     *  nothing can resolve. */
    @Test
    void aCombatThatDoesNotKnowTheCardIsLeftWithItsEmptyHand() {
        PileOperations.DrawResult result = PileOperations.draw(
                new CardPiles(List.of(), List.of(), List.of(), List.of()), 5, new RandomState(4),
                null);

        assertThat(result.drawn()).isEmpty();
        assertThat(result.random()).isEqualTo(new RandomState(4));
    }

    @Test
    void theTurnStartDealHandsOverTheDespairCard() {
        CardDefinition despair = new CardDefinition(PileOperations.DESPAIR_CARD_ID, 0,
                CardType.ATTACK, TargetingMode.ALL_ENEMIES, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(),
                List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());
        CombatState state = state(List.of(hero, foe), HERO);

        CombatEngine engine = new CombatEngine(catalog(despair), inertRelics());

        CombatResult foesTurn = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));
        assertThat(find(foesTurn.state().combatants(), FOE).piles().hand())
                .describedAs("an enemy plays a pattern, not a hand: it is never consoled")
                .isEmpty();

        CombatResult back = engine.handle(foesTurn.state(),
                new EndTurnCommand(COMBAT_ID, foesTurn.state().revision(), FOE));

        assertThat(find(back.state().combatants(), HERO).piles().hand())
                .extracting(CardInstance::definitionId)
                .containsExactly(PileOperations.DESPAIR_CARD_ID);
    }

    @Test
    void theDespairCardIsAlwaysInTheCombatCatalog() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode data = mapper.createObjectNode();
        data.put("dataVersion", "v1");
        com.fasterxml.jackson.databind.node.ArrayNode cards = data.putArray("cards");
        com.fasterxml.jackson.databind.node.ObjectNode despair = cards.addObject();
        despair.put("id", PileOperations.DESPAIR_CARD_ID);
        despair.put("cardName", "Désespoir");
        despair.put("cost", 0);
        despair.put("type", "Attaque");
        despair.put("targetingMode", "AllEnemies");
        despair.putArray("effects");
        com.fasterxml.jackson.databind.node.ObjectNode other = cards.addObject();
        other.put("id", "strike");
        other.put("cost", 1);
        other.put("type", "Attaque");
        other.put("targetingMode", "Enemy");
        other.putArray("effects");

        CardCatalog built = new fr.insastronaute.webapi.sts.combat.catalog.CardCatalogFactory()
                .create(data, "v1", List.of("strike"), "CFI");

        assertThat(built.find(PileOperations.DESPAIR_CARD_ID)).isNotNull();
    }

    /** The gift is the one card of the pile the deck did not start with. */
    private static boolean giftIsAtTheBottom(List<CardInstance> pile) {
        return !pile.getLast().instanceId().startsWith("deck-");
    }

    // ---- Cristallisation ---------------------------------------------------------------

    /**
     * The hit that fills Crystallize is the one that sets it off, as in Unity, where the extra
     * attack is asked for once the attack has landed. It used to be asked for before the attack,
     * so it waited for yet another one.
     */
    @Test
    void theHitThatFillsCrystallizeSetsItOffRightAway() {
        CombatResult result = strikeACrystallizedFoe(3, List.of());

        assertThat(playedCards(result)).containsExactly("strike", "recristallisation");
        StatusInstance crystallize = crystallizeOf(result);
        assertThat(crystallize.value())
                .as("reset, and Recristallisation's own blow does not count")
                .isEqualTo(1);
        assertThat(crystallize.progress()).isZero();
        assertThat(find(result.state().combatants(), FOE).hp()).isEqualTo(40 - 3 - 5);
    }

    @Test
    void aHitBelowTheThresholdOnlyCounts() {
        CombatResult result = strikeACrystallizedFoe(1, List.of());

        assertThat(playedCards(result)).containsExactly("strike");
        assertThat(crystallizeOf(result).value()).isEqualTo(2);
    }

    @Test
    void megacristalSetsItOffOneHitSooner() {
        CombatResult result = strikeACrystallizedFoe(2,
                List.of(new StatusInstance("Megacristal", 0, -1, "", 0)));

        assertThat(playedCards(result)).containsExactly("strike", "recristallisation");
    }

    // ---- Vol et transfert inversés -----------------------------------------------------

    @Test
    void aSelfAimedStealTakesADebuffOffTheCardsTarget() {
        CardDefinition leech = new CardDefinition("leech", 0, CardType.SKILL,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.STEAL_BUFF, 1, true)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(
                new StatusInstance("Strength", 2, -1, "", 0),
                new StatusInstance("Vuln", 0, 2, "", 0)), List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(leech), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                        new RandomState(1), HERO, List.of(FOE), leech, List.of());

        assertThat(find(resolution.combatants(), HERO).statuses())
                .extracting(StatusInstance::statusType).containsExactly("Vuln");
        assertThat(find(resolution.combatants(), FOE).statuses())
                .extracting(StatusInstance::statusType).containsExactly("Strength");
    }

    @Test
    void aSelfAimedTransferHandsOneOfTheCastersBuffsToTheCardsTarget() {
        CardDefinition gift = new CardDefinition("gift", 0, CardType.SKILL,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.TRANSFER_DEBUFF, 1, true)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(
                new StatusInstance("Weakness", 0, 2, "", 0),
                new StatusInstance("Strength", 2, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(),
                List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(gift), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                        new RandomState(1), HERO, List.of(FOE), gift, List.of());

        assertThat(find(resolution.combatants(), HERO).statuses())
                .extracting(StatusInstance::statusType).containsExactly("Weakness");
        assertThat(find(resolution.combatants(), FOE).statuses())
                .extracting(StatusInstance::statusType).containsExactly("Strength");
    }

    // ---- Ce que StatusApplied annonce ---------------------------------------------------

    /** The client shows the number the event carries: it has to be what the status now holds. */
    @Test
    void chargeStacksItsVigorAndAnnouncesTheTotal() {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30, List.of(
                new StatusInstance("Charge", 2, -1, "", 0),
                new StatusInstance("Vigor", 3, -1, "", 0)), List.of(), List.of());

        ResolutionStep step = new GainEnergyHandler().handle(COMBAT_ID, 1, HERO, hero,
                new EffectDefinition(EffectType.GAIN_ENERGY, 1, true), new RandomState(1),
                List.of(hero), timeline(HERO), null, List.of());

        CombatantState charged = step.combatants().getFirst();
        assertThat(charged.statuses()).filteredOn(status -> status.statusType().equals("Vigor"))
                .singleElement().extracting(StatusInstance::value).isEqualTo(5);
        assertThat(step.events()).filteredOn(StatusApplied.class::isInstance)
                .singleElement().extracting(event -> ((StatusApplied) event).value())
                .isEqualTo(5);
    }

    // ---- helpers -----------------------------------------------------------------------

    private static String stageAgainst(int foeHp) {
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Sadism", 0, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, foeHp, List.of(),
                List.of(), List.of());
        List<CombatantState> evolved = StatusEvolution.evolve(
                COMBAT_ID, 1, List.of(hero, foe), new ArrayList<>());
        return find(evolved, HERO).statuses().getFirst().statusType();
    }

    private static int strengthAfterDispelling(StatusInstance buff) {
        CardDefinition purge = new CardDefinition("purge", 0, CardType.SKILL,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(new EffectDefinition(
                        EffectType.DISPEL_BUFF, 1, false, null, 0, null, null, null, null,
                        List.of(), false, true, null)));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(new StatusInstance("Pavillon", 0, -1, "", 0)), List.of(), List.of());
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 20, 20, List.of(buff),
                List.of(), List.of());

        EffectResolver.Resolution resolution = new EffectResolver(catalog(purge), inertRelics())
                .resolve(COMBAT_ID, 1, List.of(hero, foe), timeline(HERO, FOE),
                        new RandomState(1), HERO, List.of(FOE), purge, List.of());

        assertThat(find(resolution.combatants(), FOE).statuses()).isEmpty();
        return strengthOf(find(resolution.combatants(), HERO));
    }

    private static CombatResult strikeACrystallizedFoe(int count, List<StatusInstance> alongside) {
        CardDefinition strike = attack("strike", 3);
        CardDefinition recrystallization = new CardDefinition("recristallisation",
                "Recristallisation", "", 0, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(CardTag.FOLLOW_UP, CardTag.EXHAUST), List.of(), List.of(
                        new EffectDefinition(EffectType.DISPEL_BUFF, 1, false, null, 50, null,
                                null),
                        new EffectDefinition(EffectType.DAMAGE, 5)));
        List<StatusInstance> foeStatuses = new ArrayList<>(alongside);
        foeStatuses.add(new StatusInstance("Crystallize", count, 5, "", 0));
        CombatantState hero = combatant(HERO, "players", ControllerType.HUMAN, 30, 30,
                List.of(), List.of(), List.of(new CardInstance("strike-1", "strike")));
        CombatantState foe = combatant(FOE, "enemies", ControllerType.AI, 40, 40, foeStatuses,
                List.of(), List.of());
        return play(catalog(strike, recrystallization), state(List.of(hero, foe), HERO),
                "strike-1", FOE);
    }

    private static StatusInstance crystallizeOf(CombatResult result) {
        return find(result.state().combatants(), FOE).statuses().stream()
                .filter(status -> status.statusType().equals("Crystallize"))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> playedCards(CombatResult result) {
        return result.events().stream()
                .filter(CardPlayed.class::isInstance)
                .map(event -> ((CardPlayed) event).definitionId())
                .toList();
    }

    private static CardDefinition attack(String id, int damage) {
        return new CardDefinition(id, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, damage)));
    }

    private static int strengthOf(CombatantState combatant) {
        return combatant.statuses().stream()
                .filter(status -> status.statusType().equals("Strength"))
                .mapToInt(StatusInstance::value)
                .sum();
    }

    private static CombatResult play(
            CardCatalog catalog, CombatState state, String cardInstanceId, String targetId) {
        return new CombatEngine(catalog, inertRelics()).handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, cardInstanceId, List.of(targetId), List.of()));
    }

    private static RelicEngine inertRelics() {
        RelicHandler inert = () -> "inert";
        return new RelicEngine(List.of(inert));
    }

    private static CardCatalog catalog(CardDefinition... cards) {
        Map<String, CardDefinition> byId = new LinkedHashMap<>();
        for (CardDefinition card : cards) {
            byId.put(card.id(), card);
        }
        return new CardCatalog("v1", byId);
    }

    private static CombatState state(List<CombatantState> combatants, String activeId) {
        List<String> order = new ArrayList<>();
        order.add(activeId);
        combatants.stream().map(CombatantState::combatantId)
                .filter(id -> !id.equals(activeId))
                .forEach(order::add);
        return new CombatState(COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null,
                new RandomState(42), combatants, timeline(order.toArray(String[]::new)), activeId);
    }

    private static List<TimelineEntry> timeline(String... order) {
        List<TimelineEntry> entries = new ArrayList<>();
        for (int index = 0; index < order.length; index++) {
            entries.add(new TimelineEntry(order[index], index * 5L, index));
        }
        return entries;
    }

    private static CombatantState combatant(
            String id,
            String team,
            ControllerType controller,
            int maxHp,
            int hp,
            List<StatusInstance> statuses,
            List<String> relics,
            List<CardInstance> hand
    ) {
        return new CombatantState(id, team, controller, maxHp, hp, 0, 3, 3, 10,
                new CardPiles(List.of(), hand, List.of(), List.of()), statuses, relics);
    }

    private static CombatantState find(List<CombatantState> combatants, String id) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
