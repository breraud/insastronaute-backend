package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoseHpHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f9");

    private final LoseHpHandler handler = new LoseHpHandler();

    @Test
    void takesTheStatedNumberOfHitPoints() {
        assertThat(remainingHp(4, 30, 30)).isEqualTo(26);
    }

    @Test
    void takesEverythingWhenTheCardAsksForMinusOne() {
        assertThat(remainingHp(-1, 30, 30))
                .describedAs("a loss of -1 is the whole of it, not a negative loss")
                .isZero();
        assertThat(remainingHp(-1, 30, 12))
                .describedAs("and never leaves a negative amount behind")
                .isZero();
    }

    private int remainingHp(int value, int maxHp, int hp) {
        EffectDefinition effect = new EffectDefinition(EffectType.LOSE_HP, value, true);
        CombatantState target = new CombatantState(
                "hero", "players", ControllerType.HUMAN, maxHp, hp, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CardDefinition card = new CardDefinition(
                "sacrifice", 0, TargetingMode.SELF, List.of(effect));

        return handler.handle(COMBAT_ID, 1, "hero", target, effect, new RandomState(1),
                        List.of(target), List.of(), card, List.of())
                .combatants().getFirst().hp();
    }
}
