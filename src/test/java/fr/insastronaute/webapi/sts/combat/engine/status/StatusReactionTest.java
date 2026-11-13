package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.effect.StatusHandler;
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
 * Statuses that only speak when a blow lands. None of them did anything before.
 */
class StatusReactionTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

        @Test
        void armsCrystallizeOnItsThirdDamagingHit() {
                StatusInstance crystallize = new StatusInstance("Crystallize", 1, 3, "", 0);

                StatusInstance afterFirst = advanced(crystallize);
                StatusInstance afterSecond = advanced(afterFirst);
                StatusInstance afterThird = advanced(afterSecond);

                assertThat(afterFirst.value()).isEqualTo(2);
                assertThat(afterFirst.progress()).isZero();
                assertThat(afterSecond.value()).isEqualTo(3);
                assertThat(afterSecond.progress()).isZero();
                assertThat(afterThird.value()).isEqualTo(3);
                assertThat(afterThird.progress()).isEqualTo(1);
        }

    @Test
    void thornsBiteWhoeverDrewBlood() {
        CombatResult result = strike(6, status("Thorns", 3, -1));

        assertThat(combatant(result, "enemy").hp()).isEqualTo(24);
        assertThat(combatant(result, "hero").hp())
                .describedAs("the thorns took three back")
                .isEqualTo(27);
    }

    /**
     * Les Épines mordent le coup, pas la blessure.
     *
     * <p>Ce test disait le contraire : une armure qui buvait tout faisait taire les Épines. C'est
     * la règle d'Unity qui tranche -- {@code OnDamageTaken} y reçoit {@code dmg} sans jamais voir
     * l'armure (EffectResolver) -- et c'est aussi ce que fait la Cristallisation, qui compte les
     * coups reçus et non les points de vie perdus. Les deux vivent du même nombre.
     */
    @Test
    void thornsBiteEvenWhenTheArmourTookTheWholeBlow() {
        CombatResult result = strike(6, 10, status("Thorns", 3, -1));

        assertThat(combatant(result, "hero").hp())
                .describedAs("le coup a porté, l'armure l'a bu, les épines mordent quand même")
                .isEqualTo(27);
    }

    @Test
    void elasticityArmoursWhoeverWasStruck() {
        CombatResult result = strike(6, status("Elastic", 4, -1));

        assertThat(combatant(result, "enemy").armor()).isEqualTo(4);
    }

    @Test
    void drawingBloodUnderAFullMoonGrowsStrength() {
        CombatResult result = strikeWithCasterStatus(6, status("FullMoon", 2, -1));

        assertThat(combatant(result, "hero").statuses())
                .extracting(StatusInstance::statusType, StatusInstance::value)
                .contains(org.assertj.core.api.Assertions.tuple("Strength", 2));
    }

    @Test
    void aVulnerabilizingBlowLeavesItsMarkOnWhoeverTookIt() {
        CombatResult result = strikeWithCasterStatus(6, status("Vulnerabilize", 2, -1));

        assertThat(combatant(result, "enemy").statuses())
                .extracting(StatusInstance::statusType)
                .contains("Vuln");
    }

    @Test
    void linkAnchoredByAnEnemyMoveIdPreventsHpLossWhileThatEnemyLives() {
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatantState anchor = new CombatantState(
                "enemy-0", "enemies", ControllerType.AI, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of(), "enemy-move:AnchorName:0", 0);
        EffectDefinition link = new EffectDefinition(
                EffectType.STATUS, 0, true, "Link", -1, null, "AnchorName");
        StatusHandler handler = new StatusHandler();

        CombatantState linkedHero = handler.handle(
                COMBAT_ID, 1, "hero", hero, link, new RandomState(1), List.of(hero, anchor),
                List.of(), new CardDefinition("link", 0, TargetingMode.SELF, List.of(link)), List.of())
                .combatants().getFirst();
        fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.HpLossVerdict verdict =
                fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.validateHpLoss(
                        linkedHero.statuses(), 8, List.of(linkedHero, anchor));

        assertThat(verdict.hpLost()).isZero();
    }

        @Test
        void miracleLeavesOneHpAndSpendsExactlyOneChargeOnALethalLoss() {
                StatusReaction.HpLossVerdict verdict = StatusReaction.validateHpLoss(
                                List.of(status("Miracle", 2, -1)), 30, 12, List.of());

                assertThat(verdict.hpLost()).isEqualTo(11);
                assertThat(verdict.statuses()).singleElement().satisfies(miracle -> {
                        assertThat(miracle.statusType()).isEqualTo("Miracle");
                        assertThat(miracle.value()).isEqualTo(1);
                });
        }

        @Test
        void miracleDoesNotSpendAChargeOnANonLethalLoss() {
                StatusReaction.HpLossVerdict verdict = StatusReaction.validateHpLoss(
                                List.of(status("Miracle", 2, -1)), 11, 12, List.of());

                assertThat(verdict.hpLost()).isEqualTo(11);
                assertThat(verdict.statuses()).singleElement()
                                .extracting(StatusInstance::value).isEqualTo(2);
        }

    private static CombatResult strike(int damage, StatusInstance... targetStatuses) {
        return strike(damage, 0, targetStatuses);
    }

    private static CombatResult strike(int damage, int targetArmor, StatusInstance... targetStatuses) {
        return play(damage, List.of(), targetArmor, List.of(targetStatuses));
    }

    private static CombatResult strikeWithCasterStatus(int damage, StatusInstance... casterStatuses) {
        return play(damage, List.of(casterStatuses), 0, List.of());
    }

    private static CombatResult play(
            int damage,
            List<StatusInstance> casterStatuses,
            int targetArmor,
            List<StatusInstance> targetStatuses
    ) {
        CardDefinition strike = new CardDefinition(
                "frappe", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, damage)));
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("played", "frappe")),
                        List.of(), List.of()),
                casterStatuses);
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, targetArmor, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), targetStatuses);
        CombatState state = new CombatState(
                COMBAT_ID, 3, "v1", CombatStatus.ACTIVE, null, new RandomState(4),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");

        return new CombatEngine(new CardCatalog("v1", Map.of("frappe", strike)),
                new RelicEngine(List.of()))
                .handle(state, new PlayCardCommand(
                        COMBAT_ID, 3, "hero", "played", List.of("enemy")));
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, "", 0);
    }

        private static StatusInstance advanced(StatusInstance crystallize) {
                return StatusReaction.toDamage(List.of(), List.of(crystallize), 1)
                                .recipientStatusUpdates()
                                .getFirst();
        }
}
