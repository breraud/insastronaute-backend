package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Les statuts ajoutés en même temps, chacun sur ce qui le distingue des autres. */
class NewStatusBehaviourTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final int PERMANENT = -1;

    // --- Absorption -------------------------------------------------------------------------

    @Test
    void absorptionLetsHalfThroughAndKeepsTheRest() {
        StatusReaction.HpLossVerdict verdict = StatusReaction.validateHpLoss(
                List.of(status("Absorption", 0, PERMANENT)), 10, List.of());

        assertThat(verdict.hpLost()).isEqualTo(5);
        assertThat(valueOf(verdict.statuses(), "Absorption")).isEqualTo(5);
    }

    @Test
    void absorptionHandsBackWhatItKeptAsTheTurnCloses() {
        StatusTurnEffect effect = StatusBehaviours.atTurnEnd(status("Absorption", 5, PERMANENT));

        assertThat(effect.hpDelta()).isEqualTo(-5);
        assertThat(effect.clearsValue())
                .describedAs("sans cela la même dette serait repayée au tour suivant")
                .isTrue();
    }

    @Test
    void absorptionWithNothingHeldDoesNothing() {
        assertThat(StatusBehaviours.atTurnEnd(status("Absorption", 0, PERMANENT)).isNothing())
                .isTrue();
    }

    // --- Fervor -----------------------------------------------------------------------------

    @Test
    void fervorSwallowsHealingAndKeepsIt() {
        StatusReaction.HealDeferral deferral = StatusReaction.deferHealing(
                List.of(status("Fervor", 0, 2)), 7);

        assertThat(deferral.remainingHeal()).isZero();
        assertThat(deferral.deferred()).isEqualTo(7);
        assertThat(valueOf(deferral.statuses(), "Fervor")).isEqualTo(7);
    }

    @Test
    void fervorHandsBackTwiceWhatItSwallowed() {
        assertThat(StatusBehaviours.onExpiry(status("Fervor", 7, 0)).hpDelta()).isEqualTo(14);
    }

    @Test
    void healingIsUntouchedWithoutFervor() {
        StatusReaction.HealDeferral deferral =
                StatusReaction.deferHealing(List.of(status("Regen", 0, 2)), 7);

        assertThat(deferral.remainingHeal()).isEqualTo(7);
        assertThat(deferral.deferred()).isZero();
    }

    // --- Confusion, Steadfast, DispelBlock ----------------------------------------------------

    /** Confusion n'affiche ni valeur ni durée, et s'en va pourtant à la fin du tour. */
    @Test
    void confusionCarriesNoNumberButStillExpires() {
        StatusInstance confusion = StatusCatalog.instantiate("Confusion", 9, 9, null, 0);

        assertThat(confusion.value()).isZero();
        assertThat(confusion.duration()).isEqualTo(1);
        assertThat(StatusCatalog.resistsEverything(confusion))
                .describedAs("cadre d'or")
                .isTrue();
    }

    @Test
    void dispelBlockStopsTheCasterRatherThanTheTarget() {
        CombatantState blocked = combatant(HERO, "players", List.of(status("DispelBlock", 0, 2)));
        CombatantState clean = combatant(ENEMY, "enemies", List.of());

        assertThat(StatusRemovalOutcome.isDispelBlocked(List.of(blocked, clean), HERO)).isTrue();
        assertThat(StatusRemovalOutcome.isDispelBlocked(List.of(blocked, clean), ENEMY)).isFalse();
    }

    // --- Adaptation ---------------------------------------------------------------------------

    @Test
    void adaptationAnswersEachCardFamilyWithItsOwnStat() {
        assertThat(adaptationGain(CardType.SKILL)).isEqualTo("Strength");
        assertThat(adaptationGain(CardType.ATTACK)).isEqualTo("Dexterity");
        assertThat(adaptationGain(CardType.POWER)).isEqualTo("Speed");
    }

    /** Elle répond à l'autre bord, jamais à son propre porteur. */
    @Test
    void adaptationIgnoresItsOwnHolder() {
        CombatantState holder = combatant(HERO, "players", List.of(status("Adaptation", 0, PERMANENT)));
        List<CombatEvent> events = new ArrayList<>();

        CardPlayReactions.Outcome outcome = CardPlayReactions.toCardPlayed(
                COMBAT_ID, 1, List.of(holder), HERO, card(CardType.SKILL), List.of(), List.of(),
                events);

        assertThat(statusesOf(outcome.combatants(), HERO))
                .extracting(StatusInstance::statusType)
                .containsExactly("Adaptation");
    }

    // --- Overpower ----------------------------------------------------------------------------

    @Test
    void overpowerRepeatsAnAttackEveryTargetFullyBlocked() {
        CardPlayReactions.Outcome outcome = attackWith(
                status("Overpower", 0, PERMANENT), blow(ENEMY, 6, 0));

        assertThat(outcome.repeatTargetIds()).containsExactly(ENEMY);
    }

    @Test
    void overpowerStaysSilentWhenAnyBloodWasDrawn() {
        CardPlayReactions.Outcome outcome = attackWith(
                status("Overpower", 0, PERMANENT), blow(ENEMY, 4, 2));

        assertThat(outcome.repeatTargetIds()).isEmpty();
    }

    // --- Time compression ---------------------------------------------------------------------

    @Test
    void timeCompressionDeletesTheTurnOfWhoeverTookAnUnblockedBlow() {
        CardPlayReactions.Outcome outcome = attackWith(
                status("TimeCompression", 0, 2), blow(ENEMY, 0, 5));

        assertThat(outcome.turnsToDelete()).containsExactly(ENEMY);
    }

    @Test
    void timeCompressionIgnoresABlowTheArmourSwallowedWhole() {
        CardPlayReactions.Outcome outcome = attackWith(
                status("TimeCompression", 0, 2), blow(ENEMY, 6, 0));

        assertThat(outcome.turnsToDelete()).isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------------

    private static String adaptationGain(CardType type) {
        CombatantState holder =
                combatant(HERO, "players", List.of(status("Adaptation", 0, PERMANENT)));
        CombatantState caster = combatant(ENEMY, "enemies", List.of());

        CardPlayReactions.toCardPlayed(COMBAT_ID, 1, List.of(holder, caster), ENEMY, card(type),
                List.of(), List.of(), new ArrayList<>());

        CardPlayReactions.Outcome outcome = CardPlayReactions.toCardPlayed(
                COMBAT_ID, 1, List.of(holder, caster), ENEMY, card(type), List.of(), List.of(),
                new ArrayList<>());
        return statusesOf(outcome.combatants(), HERO).stream()
                .map(StatusInstance::statusType)
                .filter(statusType -> !"Adaptation".equals(statusType))
                .findFirst()
                .orElse(null);
    }

    private static CardPlayReactions.Outcome attackWith(
            StatusInstance casterStatus, DamageApplied blow) {
        CombatantState caster = combatant(HERO, "players", List.of(casterStatus));
        CombatantState victim = combatant(ENEMY, "enemies", List.of());

        return CardPlayReactions.toCardPlayed(
                COMBAT_ID, 1, List.of(caster, victim), HERO, card(CardType.ATTACK),
                List.of(ENEMY), List.of(blow), new ArrayList<>());
    }

    private static DamageApplied blow(String targetId, int absorbed, int hpLost) {
        return new DamageApplied(COMBAT_ID, 1, HERO, targetId, absorbed + hpLost, absorbed,
                hpLost, 0, 20 - hpLost);
    }

    private static CardDefinition card(CardType type) {
        return new CardDefinition("tested", 1, type, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 6)));
    }

    private static CombatantState combatant(
            String id, String teamId, List<StatusInstance> statuses) {
        return new CombatantState(
                id, teamId, ControllerType.HUMAN, 30, 20, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of(), null, 0);
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, null, 0);
    }

    private static List<StatusInstance> statusesOf(
            List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .statuses();
    }

    private static Integer valueOf(List<StatusInstance> statuses, String statusType) {
        return statuses.stream()
                .filter(status -> status.statusType().equals(statusType))
                .map(StatusInstance::value)
                .findFirst()
                .orElse(null);
    }
}
