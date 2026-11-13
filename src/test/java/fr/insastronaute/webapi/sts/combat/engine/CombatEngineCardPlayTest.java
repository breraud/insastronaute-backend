package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionAction;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.EPRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GCRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MECARelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MRIERelicHandler;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineCardPlayTest {

    private static final RelicEngine RELIC_ENGINE = new RelicEngine(List.of(
            new EPRelicHandler(), new MECARelicHandler(), new GCRelicHandler(), new MRIERelicHandler()
    ));

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000051");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final CardInstance PLAYED_CARD = new CardInstance("played", "tested-card");
    private static final CardInstance OTHER_CARD = new CardInstance("other", "other-card");
    private static final CardInstance DISCARDED_CARD =
            new CardInstance("already-discarded", "other-card");

    @Test
    void playsAttackBySpendingEnergyMovingCardAndApplyingArmorThenHpDamage() {
        CardDefinition attack = definition(
                1, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 8));
        CombatState original = state(3, 0, 20, 3);

        CombatResult result = handle(original, attack, List.of(ENEMY));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().revision()).isEqualTo(original.revision() + 1);
        CombatantState hero = combatant(result.state(), HERO);
        CombatantState enemy = combatant(result.state(), ENEMY);
        assertThat(hero.energy()).isEqualTo(2);
        assertThat(hero.piles().hand()).containsExactly(OTHER_CARD);
        assertThat(hero.piles().discard()).containsExactly(DISCARDED_CARD, PLAYED_CARD);
        assertThat(enemy.armor()).isZero();
        assertThat(enemy.hp()).isEqualTo(15);

        assertThat(result.events())
                .containsExactly(
                        new CardPlayed(
                                COMBAT_ID,
                                original.revision() + 1,
                                HERO,
                                PLAYED_CARD.instanceId(),
                                PLAYED_CARD.definitionId(),
                                List.of(ENEMY)),
                        new EnergySpent(
                                COMBAT_ID, original.revision() + 1, HERO, 1, 2),
                        new DamageApplied(
                                COMBAT_ID,
                                original.revision() + 1,
                                HERO,
                                ENEMY,
                                8,
                                3,
                                5,
                                0,
                                15),
                        // The blow took the last three points of armour, and saying so is what
                        // lets a card gate an effect on having broken it.
                        new fr.insastronaute.webapi.sts.combat.event.ArmorBroken(
                                COMBAT_ID, original.revision() + 1, HERO, ENEMY, 3));
    }

    @Test
    void accumulatesSelfArmor() {
        CardDefinition defend =
                definition(1, TargetingMode.SELF, effect(EffectType.ARMOR, 6));
        CombatState original = state(3, 4, 20, 0);

        CombatResult result = handle(original, defend, List.of(HERO));

        assertThat(combatant(result.state(), HERO).armor()).isEqualTo(10);
        assertThat(result.events().get(2)).isEqualTo(
                new ArmorGained(
                        COMBAT_ID,
                        original.revision() + 1,
                        HERO,
                        HERO,
                        6,
                        6,
                        10));
    }

    @Test
    void exhaustsTheCardsSelectedByAnAuthoritativeCardSelectionEffect() {
        EffectDefinition selection = new EffectDefinition(
                EffectType.CARD_SELECTION,
                1,
                true,
                null,
                0,
                null,
                null,
                CardSelectionSource.HAND,
                CardSelectionAction.EXHAUST,
                List.of());
        CardDefinition card = definition(1, TargetingMode.SELF, selection);
        CombatState original = state(3, 0, 20, 0);

        CombatResult result = handle(original, card, List.of(HERO), List.of(OTHER_CARD.instanceId()));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.piles().hand()).isEmpty();
        assertThat(hero.piles().exhaust()).containsExactly(OTHER_CARD);
        assertThat(hero.piles().discard()).containsExactly(DISCARDED_CARD, PLAYED_CARD);
    }

    @Test
    void resolvesCardSelectionOnThePlayerEvenWhenTheCardTargetsAnEnemy() {
        CardDefinition card = definition(1, TargetingMode.SINGLE_ENEMY,
                cardSelection(1, CardSelectionSource.HAND, CardSelectionAction.EXHAUST));

        CombatResult result = handle(state(3, 0, 20, 0), card, List.of(ENEMY),
                List.of(OTHER_CARD.instanceId()));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), HERO).piles().exhaust()).containsExactly(OTHER_CARD);
        assertThat(combatant(result.state(), ENEMY).piles().exhaust()).isEmpty();
    }

    @Test
    void discardsTheSelectedCardFromHand() {
        CardDefinition card = definition(1, TargetingMode.SELF,
                cardSelection(1, CardSelectionSource.HAND, CardSelectionAction.DISCARD));

        CombatResult result = handle(state(3, 0, 20, 0), card, List.of(HERO),
                List.of(OTHER_CARD.instanceId()));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.piles().hand()).isEmpty();
        assertThat(hero.piles().discard()).containsExactly(DISCARDED_CARD, OTHER_CARD, PLAYED_CARD);
    }

    @Test
    void returnsTheSelectedDiscardCardToHand() {
        CardDefinition card = definition(1, TargetingMode.SELF,
                cardSelection(1, CardSelectionSource.DISCARD_PILE, CardSelectionAction.RETURN_TO_HAND));

        CombatResult result = handle(state(3, 0, 20, 0), card, List.of(HERO),
                List.of(DISCARDED_CARD.instanceId()));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.piles().hand()).containsExactly(OTHER_CARD, DISCARDED_CARD);
        assertThat(hero.piles().discard()).containsExactly(PLAYED_CARD);
    }

    @Test
    void allEnemiesTargetsEveryLivingOpponentServerSide() {
        CombatState original = withSecondEnemy(state(3, 0, 20, 0));
        CardDefinition attack = definition(1, TargetingMode.ALL_ENEMIES, effect(EffectType.DAMAGE, 4));

        CombatResult result = handle(original, attack, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(16);
        assertThat(combatant(result.state(), "enemy-ally").hp()).isEqualTo(16);
        assertThat(((CardPlayed) result.events().getFirst()).targetIds())
                .containsExactly(ENEMY, "enemy-ally");
    }

    @Test
    void allCharactersTargetsEveryLivingCombatantServerSide() {
        CombatState original = withLivingPlayerAlly(state(3, 0, 20, 0));
        CardDefinition attack = definition(1, TargetingMode.ALL_CHARACTERS, effect(EffectType.DAMAGE, 2));

        CombatResult result = handle(original, attack, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), HERO).hp()).isEqualTo(28);
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(18);
        assertThat(combatant(result.state(), "hero-ally").hp()).isEqualTo(18);
    }

    /**
     * Un tirage au sort sur une liste vide faisait tomber le combat.
     *
     * <p>Vu en production le 14 septembre 2026 : une suite de fin de tour cherchait une
     * cible au hasard alors que le dernier adversaire venait de mourir, dans la même
     * résolution. La commande END_TURN repartait en erreur, et la partie se bloquait là
     * — chaque nouvel envoi rejouait la même exception.</p>
     *
     * <p>Deux suites sur le même tour reproduisent exactement cela : celle qui se
     * déclenche à la fin de n'importe quel tour tue le dernier adversaire, celle qui se
     * déclenche à la fin du sien ne trouve plus personne. L'état ne peut pas être
     * construit ainsi — un combat actif exige deux camps vivants — donc le trou ne
     * s'ouvre qu'au milieu d'une résolution, ce qui est précisément la raison pour
     * laquelle il avait été oublié. Les autres modes de ciblage rendent une liste vide
     * dans ce cas, et le tirage de la confusion se garde pareil : {@code RANDOM_ENEMY}
     * était le seul chemin à ne pas le faire.</p>
     */
    @Test
    void aFollowUpFindsNoTargetRatherThanBreakingTheCombat() {
        CardDefinition followUpCard =
                definition(0, TargetingMode.RANDOM_ENEMY, effect(EffectType.DAMAGE, 4));
        // Un seul point de vie : la première suite le tue, la seconde cherche dans le vide.
        CombatState onTheBrink = withStatuses(state(3, 0, 1, 0), HERO, List.of(
                new StatusInstance("FieldTurnFollowUp", 1, -1, followUpCard.id(),
                        fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.ANY_FIELD_TURN_END),
                new StatusInstance("FieldTurnFollowUp", 1, -1, followUpCard.id(),
                        fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.OWN_TURN_END)));

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(followUpCard.id(), followUpCard)), RELIC_ENGINE);

        CombatResult result = engine.handle(
                onTheBrink, new EndTurnCommand(COMBAT_ID, onTheBrink.revision(), HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        // Le combat se termine proprement au lieu de casser au milieu du tour.
        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
    }

    @Test
    void randomEnemyUsesAndAdvancesAuthoritativeRandomState() {
        CombatState original = withSecondEnemy(state(3, 0, 20, 0));
        CardDefinition attack = definition(1, TargetingMode.RANDOM_ENEMY, effect(EffectType.DAMAGE, 4));

        CombatResult first = handle(original, attack, List.of());
        CombatResult replay = handle(original, attack, List.of());

        assertThat(first.accepted()).isTrue();
        assertThat(((CardPlayed) first.events().getFirst()).targetIds())
                .isEqualTo(((CardPlayed) replay.events().getFirst()).targetIds());
        assertThat(first.state().random()).isNotEqualTo(original.random());
    }

        @Test
            void provocationRedirectsAnAllysSingleEnemyCardAndConsumesOneCharge() {
                CombatState original = withStatus(withLivingPlayerAlly(state(3, 0, 20, 0)), "hero-ally",
                        "Provocation", 2);
                CardDefinition attack = definition(1, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 4));

                CombatResult result = handle(original, attack, List.of(ENEMY));

                assertThat(result.accepted()).isTrue();
                assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(20);
                assertThat(combatant(result.state(), "hero-ally").hp()).isEqualTo(16);
                assertThat(combatant(result.state(), "hero-ally").statuses()).singleElement()
                                .extracting(StatusInstance::value).isEqualTo(1);
                assertThat(result.events()).filteredOn(CardPlayed.class::isInstance).singleElement()
                        .extracting(event -> ((CardPlayed) event).targetIds()).isEqualTo(List.of("hero-ally"));
        }

            @Test
            void analyseGrantsArmorWhenAnOpponentReceivesADebuff() {
                CombatState original = withStatus(state(3, 0, 20, 0), HERO, "Analyse", 3);
                CardDefinition scan = definition(1, TargetingMode.SINGLE_ENEMY,
                        new EffectDefinition(EffectType.STATUS, 0, false, "Vuln", 2, null, null));

                CombatResult result = handle(original, scan, List.of(ENEMY));

                assertThat(result.accepted()).isTrue();
                assertThat(combatant(result.state(), HERO).armor()).isEqualTo(3);
                assertThat(result.events()).filteredOn(ArmorGained.class::isInstance)
                        .singleElement()
                        .extracting(event -> ((ArmorGained) event).requestedArmor()).isEqualTo(3);
            }

            @Test
            void compilationDelaysTheNextTurnWhenItsHolderLosesHpOnTheirTurn() {
                CombatState original = withStatus(state(3, 0, 20, 0), HERO, "Compilation", 50);
                CardDefinition recoil = definition(1, TargetingMode.SELF,
                        new EffectDefinition(EffectType.LOSE_HP, 5, true));

                CombatResult result = handle(original, recoil, List.of(HERO));

                assertThat(result.accepted()).isTrue();
                assertThat(combatant(result.state(), HERO).hp()).isEqualTo(25);
                assertThat(entry(result.state(), HERO).pendingSelfDelayTicks()).isEqualTo(5);
            }

            @Test
            void compilationAlsoAnswersHpLostByEndOfTurnStatuses() {
                CombatState original = withStatuses(state(3, 0, 20, 0), HERO,
                        List.of(new StatusInstance("Compilation", 50, -1, "", 0),
                                new StatusInstance("Burn", 0, 4, "", 0)));

                CombatResult result = new CombatEngine(
                        new CardCatalog("v1", Map.of("tested-card", definition(1, TargetingMode.SELF))),
                        RELIC_ENGINE)
                        .handle(original, new EndTurnCommand(COMBAT_ID, original.revision(), HERO));

                assertThat(result.accepted()).isTrue();
                assertThat(combatant(result.state(), HERO).hp()).isEqualTo(26);
                assertThat(entry(result.state(), HERO).readyAtTick()).isEqualTo(15);
            }

    @Test
    void saturatesArmorAndReportsOnlyTheCapacityActuallyApplied() {
        CardDefinition defend =
                definition(0, TargetingMode.SELF, effect(EffectType.ARMOR, 1));
        CombatState original = state(0, Integer.MAX_VALUE, 20, 0);

        CombatResult result = handle(original, defend, List.of(HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), HERO).armor()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.events().get(2)).isEqualTo(
                new ArmorGained(
                        COMBAT_ID,
                        original.revision() + 1,
                        HERO,
                        HERO,
                        1,
                        0,
                        Integer.MAX_VALUE));
    }

    @Test
    void resolvesMultipleEffectsInDefinitionOrder() {
        CardDefinition combination = definition(
                2,
                TargetingMode.SINGLE_ENEMY,
                new EffectDefinition(EffectType.DAMAGE, 5, false),
                new EffectDefinition(EffectType.ARMOR, 4, true));
        CombatState original = state(3, 0, 20, 2);

        CombatResult result = handle(original, combination, List.of(ENEMY));

        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        DamageApplied.class,
                        fr.insastronaute.webapi.sts.combat.event.ArmorBroken.class,
                        ArmorGained.class);
        assertThat(combatant(result.state(), HERO).energy()).isEqualTo(1);
        assertThat(combatant(result.state(), HERO).armor()).isEqualTo(4);
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(17);
        assertThat(combatant(result.state(), ENEMY).armor()).isZero();
        assertThat(result.events().get(2)).isEqualTo(
                new DamageApplied(
                        COMBAT_ID,
                        original.revision() + 1,
                        HERO,
                        ENEMY,
                        5,
                        2,
                        3,
                        0,
                        17));
        // Index four, not three: the blow that stripped the enemy's armour announces the break
        // between the damage and the self-armour.
        assertThat(result.events().get(4)).isEqualTo(
                new ArmorGained(
                        COMBAT_ID,
                        original.revision() + 1,
                        HERO,
                        HERO,
                        4,
                        4,
                        4));
    }

    @Test
    void finishesAsADrawOnlyAfterBothCombatantsAreKnockedOut() {
        CardDefinition mutualDestruction = definition(
                1,
                TargetingMode.SINGLE_ENEMY,
                new EffectDefinition(EffectType.DAMAGE, 50, false),
                new EffectDefinition(EffectType.DAMAGE, 50, true));
        CombatState original = state(3, 0, 7, 0);

        CombatResult result = handle(original, mutualDestruction, List.of(ENEMY));

        assertThat(combatant(result.state(), HERO).hp()).isZero();
        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.state().timeline()).isEmpty();
        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        DamageApplied.class,
                        DamageApplied.class,
                        CombatEnded.class);
        assertThat(result.events().getLast()).isEqualTo(
                new CombatEnded(COMBAT_ID, original.revision() + 1, null));
    }

    @Test
    void completesLethalPlayOnlyAfterApplyingItsRemainingEffects() {
        CardDefinition lethalThenArmor = definition(
                1,
                TargetingMode.SINGLE_ENEMY,
                new EffectDefinition(EffectType.DAMAGE, 50, false),
                new EffectDefinition(EffectType.ARMOR, 4, true));
        CombatState original = state(3, 0, 7, 0);

        CombatResult result = handle(original, lethalThenArmor, List.of(ENEMY));

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(combatant(result.state(), HERO).armor()).isEqualTo(4);
        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        DamageApplied.class,
                        ArmorGained.class,
                        CombatEnded.class);
        assertThat(result.events().getLast()).isEqualTo(
                new CombatEnded(COMBAT_ID, original.revision() + 1, "players"));
    }

    @Test
    void clampsOverkillDamageAtZeroHpAndReportsExactHpLoss() {
        CardDefinition attack = definition(
                1, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 50));
        CombatState original = withLivingPlayerAlly(state(3, 0, 7, 2));

        CombatResult result = handle(original, attack, List.of(ENEMY));

        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(combatant(result.state(), ENEMY).armor()).isZero();
        assertThat(result.events().get(2)).isEqualTo(
                new DamageApplied(
                        COMBAT_ID,
                        original.revision() + 1,
                        HERO,
                        ENEMY,
                        50,
                        2,
                        7,
                        0,
                        0));
        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo("players");
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.state().timeline())
                .containsExactly(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry("hero-ally", 2, 2));
        assertThat(result.events().getLast()).isEqualTo(
                new CombatEnded(COMBAT_ID, original.revision() + 1, "players"));
        assertThat(combatant(result.state(), HERO).energy()).isEqualTo(2);
        assertThat(combatant(result.state(), HERO).piles().hand()).containsExactly(OTHER_CARD);
        assertThat(combatant(result.state(), HERO).piles().discard())
                .containsExactly(DISCARDED_CARD, PLAYED_CARD);
        assertThat(original.status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(combatant(original, ENEMY).hp()).isEqualTo(7);

        CombatResult futureCommand = new CombatEngine(
                new CardCatalog("v1", Map.of(attack.id(), attack)), RELIC_ENGINE)
                .handle(result.state(), new EndTurnCommand(
                        COMBAT_ID, result.state().revision(), HERO));
        assertThat(futureCommand.state()).isSameAs(result.state());
        assertThat(futureCommand.events()).isEmpty();
        assertThat(futureCommand.rejection().code()).isEqualTo(RejectionCode.COMBAT_FINISHED);
    }

    @Test
    void staysActiveWhenAnotherMemberOfTheDefeatedCombatantsTeamLives() {
        CardDefinition attack = definition(
                1, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 50));
        CombatState original = withSecondEnemy(state(3, 0, 7, 0));

        CombatResult result = handle(original, attack, List.of(ENEMY));

        assertThat(combatant(result.state(), ENEMY).hp()).isZero();
        assertThat(combatant(result.state(), "enemy-ally").hp()).isEqualTo(20);
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(result.state().timeline()).isEqualTo(original.timeline());
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
    }

    @Test
    void emitsEnergySpentForAZeroCostCard() {
        CardDefinition freeAttack = definition(
                0, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 1));
        CombatState original = state(0, 0, 20, 0);

        CombatResult result = handle(original, freeAttack, List.of(ENEMY));

        assertThat(combatant(result.state(), HERO).energy()).isZero();
        assertThat(result.events().get(1)).isEqualTo(
                new EnergySpent(
                        COMBAT_ID, original.revision() + 1, HERO, 0, 0));
    }

    @Test
    void acceptsDamageThenDrawAndKeepsTheResolvingCardOutOfTheReshuffle() {
        CardDefinition damageThenDraw = definition(
                1,
                TargetingMode.SINGLE_ENEMY,
                effect(EffectType.DAMAGE, 5),
                new EffectDefinition(EffectType.DRAW, 1, true));
        CombatState original = state(3, 0, 20, 0);

        CombatResult result = handle(original, damageThenDraw, List.of(ENEMY));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(15);
        assertThat(combatant(result.state(), HERO).piles().hand())
                .containsExactly(OTHER_CARD, DISCARDED_CARD);
        assertThat(combatant(result.state(), HERO).piles().discard())
                .containsExactly(PLAYED_CARD);
        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        DamageApplied.class,
                        CardDrawn.class);
    }

    @Test
    void acceptsGainEnergyAfterMovingAndPayingForTheCard() {
        CardDefinition gainEnergy = definition(
                1, TargetingMode.SELF, effect(EffectType.GAIN_ENERGY, 2));
        CombatState original = state(3, 0, 20, 0);

        CombatResult result = handle(original, gainEnergy, List.of(HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), HERO).energy()).isEqualTo(4);
        assertThat(combatant(result.state(), HERO).piles().hand()).containsExactly(OTHER_CARD);
        assertThat(combatant(result.state(), HERO).piles().discard())
                .containsExactly(DISCARDED_CARD, PLAYED_CARD);
        assertThat(result.events().get(2))
                .isEqualTo(new EnergyGained(COMBAT_ID, 8, HERO, 2, 4));
    }

    @Test
    void leavesInputStateUnchangedAndCardPlayedDefensivelyCopiesTargets() {
        CardDefinition attack = definition(
                1, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 2));
        CombatState original = state(3, 0, 20, 0);
        CombatantState originalHero = combatant(original, HERO);
        CombatantState originalEnemy = combatant(original, ENEMY);

        CombatResult result = handle(original, attack, List.of(ENEMY));

        assertThat(original.revision()).isEqualTo(7);
        assertThat(originalHero.energy()).isEqualTo(3);
        assertThat(originalHero.piles().hand()).containsExactly(PLAYED_CARD, OTHER_CARD);
        assertThat(originalHero.piles().discard()).containsExactly(DISCARDED_CARD);
        assertThat(originalEnemy.hp()).isEqualTo(20);
        assertThat(originalEnemy.armor()).isZero();
        assertThat(result.state().random()).isSameAs(original.random());
        assertThat(result.state().timeline()).isSameAs(original.timeline());
        assertThat(result.state().dataVersion()).isEqualTo(original.dataVersion());
        assertThat(result.state().status()).isEqualTo(original.status());
        assertThat(result.state().activeCombatantId()).isEqualTo(original.activeCombatantId());

        List<String> mutableTargets = new ArrayList<>(List.of(ENEMY));
        CardPlayed event = new CardPlayed(
                COMBAT_ID, 8, HERO, "instance", "definition", mutableTargets);
        mutableTargets.clear();
        assertThat(event.targetIds()).containsExactly(ENEMY);
        assertThatThrownBy(() -> event.targetIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationRejectionsStillReturnTheOriginalStateWithoutEvents() {
        CardDefinition attack = definition(
                2, TargetingMode.SINGLE_ENEMY, effect(EffectType.DAMAGE, 8));
        CombatState noEnergy = state(1, 0, 20, 0);

        CombatResult insufficient = handle(noEnergy, attack, List.of(ENEMY));
        CombatResult invalidTarget = handle(state(3, 0, 20, 0), attack, List.of(HERO));

        assertRejectedWithoutTransition(noEnergy, insufficient);
        assertThat(insufficient.rejection().code()).isEqualTo(RejectionCode.INSUFFICIENT_ENERGY);
        assertThat(invalidTarget.accepted()).isFalse();
        assertThat(invalidTarget.events()).isEmpty();
        assertThat(invalidTarget.rejection().code()).isEqualTo(RejectionCode.INVALID_TARGET);
    }

    private static void assertRejectedWithoutTransition(
            CombatState original,
            CombatResult result
    ) {
        assertThat(result.accepted()).isFalse();
        assertThat(result.state()).isSameAs(original);
        assertThat(result.state()).isEqualTo(original);
        assertThat(result.events()).isEmpty();
    }

    private static CombatResult handle(
            CombatState state,
            CardDefinition definition,
            List<String> targetIds
    ) {
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of(definition.id(), definition)), RELIC_ENGINE);
        return engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        PLAYED_CARD.instanceId(),
                        targetIds));
    }

    private static CombatResult handle(
            CombatState state,
            CardDefinition definition,
            List<String> targetIds,
            List<String> selectedCardInstanceIds
    ) {
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of(definition.id(), definition)), RELIC_ENGINE);
        return engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        PLAYED_CARD.instanceId(),
                        targetIds,
                        selectedCardInstanceIds));
    }

    private static CardDefinition definition(
            int cost,
            TargetingMode targetingMode,
            EffectDefinition... effects
    ) {
        return new CardDefinition("tested-card", cost, targetingMode, List.of(effects));
    }

    private static EffectDefinition effect(EffectType type, int value) {
        return new EffectDefinition(type, value);
    }

    private static EffectDefinition cardSelection(
            int value,
            CardSelectionSource source,
            CardSelectionAction action
    ) {
        return new EffectDefinition(EffectType.CARD_SELECTION, value, true, null, 0, null, null,
                source, action, List.of());
    }

    private static CombatState state(
            int heroEnergy,
            int heroArmor,
            int enemyHp,
            int enemyArmor
    ) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                heroArmor,
                heroEnergy,
                3,
                10,
                new CardPiles(
                        List.of(),
                        List.of(PLAYED_CARD, OTHER_CARD),
                        List.of(DISCARDED_CARD),
                        List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                enemyHp,
                enemyArmor,
                2,
                2,
                12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY, 1, 1)),
                HERO);
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

        private static TimelineEntry entry(CombatState state, String combatantId) {
                return state.timeline().stream()
                                .filter(candidate -> candidate.combatantId().equals(combatantId))
                                .findFirst()
                                .orElseThrow();
        }

    private static CombatState withSecondEnemy(CombatState state) {
        CombatantState enemyAlly = new CombatantState(
                "enemy-ally",
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        List<CombatantState> combatants = new ArrayList<>(state.combatants());
        combatants.add(enemyAlly);
        List<TimelineEntry> timeline = new ArrayList<>(state.timeline());
        timeline.add(new TimelineEntry("enemy-ally", 2, 2));
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                combatants,
                timeline,
                state.activeCombatantId());
    }

        private static CombatState withStatus(
                        CombatState state, String combatantId, String statusType, int value) {
                return withStatuses(state, combatantId,
                                List.of(new StatusInstance(statusType, value, -1, "", 0)));
        }

        private static CombatState withStatuses(
                        CombatState state, String combatantId, List<StatusInstance> statuses) {
                return new CombatState(
                                state.combatId(), state.revision(), state.dataVersion(), state.status(),
                                state.winnerTeamId(), state.random(),
                                state.combatants().stream().map(combatant -> {
                                        if (!combatant.combatantId().equals(combatantId)) {
                                                return combatant;
                                        }
                                        return new CombatantState(
                                                        combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                                                        combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                                                        combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                                                        statuses,
                                                        combatant.relics(), combatant.intentCardId(), combatant.patternIndex());
                                }).toList(), state.timeline(), state.activeCombatantId());
        }

    private static CombatState withLivingPlayerAlly(CombatState state) {
        CombatantState heroAlly = new CombatantState(
                "hero-ally",
                "players",
                ControllerType.HUMAN,
                20,
                20,
                0,
                3,
                3,
                10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        List<CombatantState> combatants = new ArrayList<>(state.combatants());
        combatants.add(heroAlly);
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                combatants,
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY, 1, 1),
                        new TimelineEntry("hero-ally", 2, 2)),
                state.activeCombatantId());
    }
}
