package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Two effects that reshape the statuses a combatant already carries.
 *
 * <p>Both used to read the wrong number. Unity doubles a debuff by applying it a second time
 * ({@code EffectResolver.cs:844-849} → {@code StatusEffect.Merge}), which adds turns to a timed
 * debuff and value to a permanent one; and it extends a status by adding to its <em>duration</em>
 * only, skipping the ones marked inextendable ({@code EffectResolver.cs:875-881}). Doubling the
 * value of Vulnerable — whose magnitude lives in its duration and whose value is 0 — did nothing
 * at all.
 */
class StatusReshapingEffectsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void doublesTheRemainingTurnsOfATimedDebuff() {
        CombatantState target = carrying(new StatusInstance("Vuln", 0, 2, null, 0));

        CombatantState updated = doubleDebuffs(target);

        assertThat(status(updated, "Vuln").duration()).isEqualTo(4);
    }

    @Test
    void doublesTheValueOfAPermanentDebuff() {
        CombatantState target = carrying(new StatusInstance("Strength", -3, -1, null, 0));

        CombatantState updated = doubleDebuffs(target);

        assertThat(status(updated, "Strength").value()).isEqualTo(-6);
    }

    @Test
    void leavesABuffAloneWhenDoublingDebuffs() {
        CombatantState target = carrying(new StatusInstance("Thorns", 3, 2, null, 0));

        CombatantState updated = doubleDebuffs(target);

        assertThat(status(updated, "Thorns").value()).isEqualTo(3);
        assertThat(status(updated, "Thorns").duration()).isEqualTo(2);
    }

    @Test
    void addsTurnsRatherThanValueWhenExtendingAStatus() {
        CombatantState target = carrying(new StatusInstance("Vuln", 0, 2, null, 0));

        CombatantState updated = extendStatuses(target, 2);

        assertThat(status(updated, "Vuln").duration()).isEqualTo(4);
        assertThat(status(updated, "Vuln").value()).isZero();
    }

    @Test
    void refusesToExtendAStatusMarkedInextendable() {
        CombatantState target = carrying(new StatusInstance("Stun", 0, 2, null, 0));

        CombatantState updated = extendStatuses(target, 2);

        assertThat(status(updated, "Stun").duration()).isEqualTo(2);
    }

    @Test
    void refusesToExtendAPermanentStatus() {
        CombatantState target = carrying(new StatusInstance("Strength", 3, -1, null, 0));

        CombatantState updated = extendStatuses(target, 2);

        assertThat(status(updated, "Strength").duration()).isEqualTo(-1);
        assertThat(status(updated, "Strength").value()).isEqualTo(3);
    }

    private static CombatantState doubleDebuffs(CombatantState target) {
        return resolve(new DoubleDebuffsHandler(),
                new EffectDefinition(EffectType.DOUBLE_DEBUFFS, 0, false, null, 0, "", null),
                target);
    }

    private static CombatantState extendStatuses(CombatantState target, int turns) {
        return resolve(new ExtendStatusesHandler(),
                new EffectDefinition(EffectType.EXTEND_STATUSES, turns, false, null, 0, "", null),
                target);
    }

    private static CombatantState resolve(
            EffectHandler handler,
            EffectDefinition effect,
            CombatantState target
    ) {
        ResolutionStep step = handler.handle(
                COMBAT_ID,
                1L,
                "caster",
                target,
                effect,
                new RandomState(7),
                List.of(target),
                List.of(),
                null,
                List.of());
        return step.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(target.combatantId()))
                .findFirst()
                .orElseThrow();
    }

    private static StatusInstance status(CombatantState combatant, String statusType) {
        return combatant.statuses().stream()
                .filter(status -> status.statusType().equals(statusType))
                .findFirst()
                .orElseThrow();
    }

    private static CombatantState carrying(StatusInstance... statuses) {
        return new CombatantState(
                "target",
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                0,
                0,
                12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(statuses),
                List.of(),
                null,
                0);
    }
}
