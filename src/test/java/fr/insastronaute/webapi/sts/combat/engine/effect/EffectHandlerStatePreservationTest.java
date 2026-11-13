package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.EffectResolver;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An effect changes the field it is about and nothing else.
 *
 * <p>Every handler rebuilds the combatant it touches, and a rebuild that forgets a field resets
 * it: an enemy whose armour is raised must not lose the intent it announced nor its position in
 * its move pattern, or it silently restarts its pattern every time it is hit.
 */
class EffectHandlerStatePreservationTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String ACTOR = "enemy";
    private static final String OPPONENT = "hero";

    private static List<EffectDefinition> effectsThatRebuildTheirTarget() {
        return List.of(
                new EffectDefinition(EffectType.DAMAGE, 3),
                new EffectDefinition(EffectType.ARMOR, 4),
                new EffectDefinition(EffectType.HEAL, 2),
                new EffectDefinition(EffectType.LOSE_HP, 1),
                new EffectDefinition(EffectType.GAIN_ENERGY, 1),
                new EffectDefinition(EffectType.DRAW, 1),
                new EffectDefinition(EffectType.DISCARD, 1),
                new EffectDefinition(EffectType.EXHAUST, 1),
                new EffectDefinition(EffectType.BREAK, 1),
                new EffectDefinition(EffectType.GRAVITY, 1),
                new EffectDefinition(EffectType.DOUBLE_DEBUFFS, 0),
                new EffectDefinition(EffectType.EXTEND_STATUSES, 1),
                new EffectDefinition(EffectType.DISPEL_BUFF, -1),
                new EffectDefinition(EffectType.DISPEL_DEBUFF, -1),
                new EffectDefinition(EffectType.STATUS, 2, false, "Weakness", 2, null, null),
                new EffectDefinition(EffectType.SET_STATUS_TO_MAX_VALUE, 0, false, "Poison", 0, null, null),
                new EffectDefinition(EffectType.DISPEL_SPECIFIC_STATUS, 1, false, "Poison", 0, null, null),
                new EffectDefinition(EffectType.STEAL_BUFF, 1),
                new EffectDefinition(EffectType.TRANSFER_DEBUFF, 1),
                new EffectDefinition(EffectType.ADD_CARD_TO_HAND, 1, false, null, 0, null, "frappe"),
                new EffectDefinition(EffectType.ADD_CARD_TO_DRAW_PILE, 1, false, null, 0, null, "frappe"),
                new EffectDefinition(EffectType.ADD_CARD_TO_DISCARD_PILE, 1, false, null, 0, null, "frappe"),
                new EffectDefinition(EffectType.ADD_RANDOM_CARD, 1),
                new EffectDefinition(EffectType.FORCE_NEXT_CARD, 1, false, null, 0, null, "frappe"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("effectsThatRebuildTheirTarget")
    void keepsTheAnnouncedIntentAndThePatternPositionOfEveryCombatantItRebuilds(
            EffectDefinition effect
    ) {
        EffectResolver.Resolution resolution = resolve(effect);

        assertThat(resolution.combatants()).allSatisfy(combatant -> {
            assertThat(combatant.intentCardId())
                    .describedAs("intent of %s", combatant.combatantId())
                    .isEqualTo("frappe");
            assertThat(combatant.patternIndex())
                    .describedAs("pattern position of %s", combatant.combatantId())
                    .isEqualTo(2);
        });
    }

    @Test
    void alsoKeepsThemWhenTheEffectTargetsTheCasterItself() {
        EffectResolver.Resolution resolution =
                resolve(new EffectDefinition(EffectType.ARMOR, 5, true));

        assertThat(resolution.combatants()).allSatisfy(combatant -> {
            assertThat(combatant.intentCardId()).isEqualTo("frappe");
            assertThat(combatant.patternIndex()).isEqualTo(2);
        });
    }

    private static EffectResolver.Resolution resolve(EffectDefinition effect) {
        CardCatalog catalog = new CardCatalog("v1", Map.of(
                "frappe", new CardDefinition("frappe", 1, TargetingMode.SINGLE_ENEMY,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 6)))));
        CardDefinition card = new CardDefinition(
                "tested-card", 0, TargetingMode.SINGLE_ENEMY, List.of(effect));

        return new EffectResolver(catalog, new RelicEngine(List.of())).resolve(
                COMBAT_ID,
                1,
                List.of(combatant(ACTOR, "enemies"), combatant(OPPONENT, "players")),
                List.of(),
                new RandomState(11),
                ACTOR,
                List.of(OPPONENT),
                card,
                List.of());
    }

    private static CombatantState combatant(String id, String team) {
        return new CombatantState(
                id, team, ControllerType.AI, 30, 25, 3, 3, 3, 10,
                new CardPiles(
                        List.of(new CardInstance(id + "-draw", "frappe")),
                        List.of(new CardInstance(id + "-hand", "frappe")),
                        List.of(),
                        List.of()),
                List.of(
                        new StatusInstance("Strength", 2, -1, "", 0),
                        new StatusInstance("Poison", 3, 2, "", 0)),
                List.of(),
                "frappe",
                2);
    }
}
