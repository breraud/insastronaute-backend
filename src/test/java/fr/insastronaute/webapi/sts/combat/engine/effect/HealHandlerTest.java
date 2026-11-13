package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private final HealHandler handler = new HealHandler(new RelicEngine(List.of()));

    @Test
    void healsUpToTheMissingHitPointsAndReportsIt() {
        ResolutionStep step = heal(new EffectDefinition(EffectType.HEAL, 4), wounded(20, 30));

        assertThat(step.combatants()).singleElement()
                .extracting(CombatantState::hp)
                .isEqualTo(24);
        assertThat(step.events()).singleElement()
                .isInstanceOfSatisfying(HealApplied.class, event -> {
                    assertThat(event.targetId()).isEqualTo("hero");
                    assertThat(event.requestedHeal()).isEqualTo(4);
                    assertThat(event.actualHeal()).isEqualTo(4);
                    assertThat(event.remainingHp()).isEqualTo(24);
                });
    }

    @Test
    void neverHealsPastTheMaximum() {
        ResolutionStep step = heal(new EffectDefinition(EffectType.HEAL, 40), wounded(28, 30));

        assertThat(step.combatants()).singleElement()
                .extracting(CombatantState::hp)
                .isEqualTo(30);
    }

    @Test
    void undeadTurnsHealingIntoReportedHitPointLoss() {
        CombatantState undead = wounded(20, 30, List.of(
                new StatusInstance("Undead", 0, 99, "", 0)));

        ResolutionStep step = heal(new EffectDefinition(EffectType.HEAL, 4), undead);

        assertThat(step.combatants()).singleElement()
                .extracting(CombatantState::hp)
                .isEqualTo(16);
        assertThat(step.events()).singleElement()
                .isInstanceOfSatisfying(HpLost.class, event -> {
                    assertThat(event.requestedLoss()).isEqualTo(4);
                    assertThat(event.actualLoss()).isEqualTo(4);
                    assertThat(event.remainingHp()).isEqualTo(16);
                });
    }

    private ResolutionStep heal(EffectDefinition effect, CombatantState target) {
        CardDefinition card = new CardDefinition(
                "soin", 1, TargetingMode.SELF, List.of(effect));
        return handler.handle(COMBAT_ID, 1, "hero", target, effect, new RandomState(3),
                List.of(target), List.of(), card, List.of());
    }

    private static CombatantState wounded(int hp, int maxHp) {
        return wounded(hp, maxHp, List.of());
    }

    private static CombatantState wounded(int hp, int maxHp, List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "players", ControllerType.HUMAN, maxHp, hp, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), statuses);
    }
}
