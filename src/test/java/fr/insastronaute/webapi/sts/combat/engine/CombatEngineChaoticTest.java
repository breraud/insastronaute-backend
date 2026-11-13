package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Chaos, joué pour de vrai : plus aucun coup ne porte, mais tout le reste part double.
 */
class CombatEngineChaoticTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000ca");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final int FULL_HP = 40;
    private static final int WOUNDED = 20;
    private static final int PERMANENT = -1;

    // --- ce que Chaos laisse tel quel ----------------------------------------------------------

    @Test
    void aChaoticCasterDealsNoDamage() {
        assertThat(enemyAfter(damageCard(), true).hp()).isEqualTo(FULL_HP);
    }

    @Test
    void theSameBlowLandsWithoutChaos() {
        assertThat(enemyAfter(damageCard(), false).hp()).isEqualTo(FULL_HP - 9);
    }

    /** L'armure est laissée telle quelle : Chaos ne récompense pas d'en donner à l'adversaire. */
    @Test
    void armourIsLeftAsAuthored() {
        assertThat(enemyAfter(card(EffectType.ARMOR, 5, 0), true).armor()).isEqualTo(5);
    }

    // --- ce que Chaos double -------------------------------------------------------------------

    @Test
    void aStatusAimedAtTheOtherSideLastsTwiceAsLong() {
        assertThat(durationOf(after(statusCard(), true), "Vuln")).isEqualTo(4);
    }

    @Test
    void theSameStatusIsOrdinaryWithoutChaos() {
        assertThat(durationOf(after(statusCard(), false), "Vuln")).isEqualTo(2);
    }

    /** Une perte de PV n'est pas un coup : elle double. */
    @Test
    void hpLossAimedAtTheOtherSideIsDoubled() {
        assertThat(enemyAfter(card(EffectType.LOSE_HP, 4, 0), true).hp())
                .isEqualTo(FULL_HP - 8);
    }

    /** Un tour repoussé l'est deux fois plus loin. */
    @Test
    void aDelayAimedAtTheOtherSideIsDoubled() {
        long delayed = tickOf(after(card(EffectType.DELAY_TURN, 10, 0), true), ENEMY);
        long ordinary = tickOf(after(card(EffectType.DELAY_TURN, 10, 0), false), ENEMY);

        assertThat(delayed - 12).isEqualTo((ordinary - 12) * 2);
    }

    /** Même un soin, s'il part vers l'adversaire : la règle se lit sur le bord, pas sur l'intention. */
    @Test
    void evenAHealAimedAtTheOtherSideIsDoubled() {
        assertThat(enemyAfter(card(EffectType.HEAL, 5, 0), true, WOUNDED).hp())
                .isEqualTo(WOUNDED + 10);
    }

    /** Deux fois un permanent est encore un permanent : la durée n'est pas un compte à doubler. */
    @Test
    void aPermanentDurationIsNotDoubled() {
        CombatResult result = after(new CardDefinition(
                "blow", 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.STATUS, 3, false, "Strength", PERMANENT, null, null))), true);

        assertThat(combatant(result, ENEMY).statuses())
                .filteredOn(status -> "Strength".equals(status.statusType()))
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.duration()).isEqualTo(PERMANENT);
                    assertThat(status.value()).describedAs("la valeur, elle, double").isEqualTo(6);
                });
    }

    /** Ce que le porteur se réserve garde sa taille. */
    @Test
    void whatTheBearerKeepsForItselfIsNotDoubled() {
        CardDefinition selfHeal = new CardDefinition(
                "blow", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.HEAL, 5, true)));

        assertThat(combatant(play(selfHeal, true, WOUNDED, HERO), HERO).hp())
                .isEqualTo(WOUNDED + 5);
    }

    // --- montage -------------------------------------------------------------------------------

    private static CombatantState enemyAfter(CardDefinition card, boolean chaotic) {
        return enemyAfter(card, chaotic, FULL_HP);
    }

    private static CombatantState enemyAfter(CardDefinition card, boolean chaotic, int enemyHp) {
        return combatant(play(card, chaotic, enemyHp, ENEMY), ENEMY);
    }

    private static CombatResult after(CardDefinition card, boolean chaotic) {
        return play(card, chaotic, FULL_HP, ENEMY);
    }

    private static int durationOf(CombatResult result, String statusType) {
        return combatant(result, ENEMY).statuses().stream()
                .filter(status -> statusType.equals(status.statusType()))
                .map(StatusInstance::duration)
                .findFirst()
                .orElseThrow();
    }

    private static long tickOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .readyAtTick();
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CardDefinition damageCard() {
        return card(EffectType.DAMAGE, 9, 0);
    }

    private static CardDefinition statusCard() {
        return new CardDefinition(
                "blow", 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.STATUS, 0, false, "Vuln", 2, null, null)));
    }

    private static CardDefinition card(EffectType type, int value, int duration) {
        return new CardDefinition(
                "blow", 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(type, value, false, null, duration, null, null)));
    }

    private static CombatResult play(
            CardDefinition definition, boolean chaotic, int enemyHp, String targetId) {
        CardInstance card = new CardInstance("played", definition.id());
        List<StatusInstance> statuses = chaotic
                ? List.of(new StatusInstance("Chaotic", 0, -1, null, 0))
                : List.of();

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, FULL_HP, enemyHp, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                statuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, FULL_HP, enemyHp, 0, 0, 0, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(3),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 10), new TimelineEntry(ENEMY, 12, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        return engine.handle(state,
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(), List.of(targetId)));
    }
}
