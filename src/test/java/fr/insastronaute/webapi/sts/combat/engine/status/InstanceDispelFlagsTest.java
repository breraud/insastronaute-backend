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
 * One instance of a status can resist removal where another of the same kind does not.
 *
 * <p>In Unity the flags live on the instance: Titan Heart builds its Fragile and marks that one
 * as unremovable ({@code TitanHeartRelic.cs:19-21}), and Cathedral Knell does the same with its
 * opening Weakness. The catalog here answers per kind, so the drawback those relics are supposed
 * to carry could be dispelled away — the player kept the bonuses and cancelled the price.
 */
class InstanceDispelFlagsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000151");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void keepsAnInstanceMarkedUnremovableThroughADispel() {
        CombatResult result = dispelDebuffs(
                StatusInstance.unremovable("Fragile", 0, 99),
                new StatusInstance("Vuln", 0, 2, "", 1));

        assertThat(hero(result).statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("Fragile");
    }

    @Test
    void stillDispelsAnOrdinaryInstanceOfTheSameKind() {
        CombatResult result = dispelDebuffs(new StatusInstance("Fragile", 0, 99, "", 0));

        assertThat(hero(result).statuses()).isEmpty();
    }

    @Test
    void readsTheCatalogWhenTheInstanceSaysNothing() {
        assertThat(StatusCatalog.isDispellable(new StatusInstance("Fragile", 0, 2, "", 0), false))
                .isTrue();
        assertThat(StatusCatalog.isDispellable(StatusInstance.unremovable("Fragile", 0, 2), false))
                .isFalse();
    }

    @Test
    void refusesAnUnremovableInstanceEvenToAnEffectThatIgnoresResistance() {
        assertThat(StatusCatalog.isDispellable(StatusInstance.unremovable("Fragile", 0, 2), true))
                .isFalse();
    }

    @Test
    void keepsTheFragileTitanHeartChargesYouWith() {
        CombatantState wearer = new fr.insastronaute.webapi.sts.combat.engine.relic.handlers
                .TitanHeartRelic()
                .onCombatStart(null, bare(), new java.util.ArrayList<>());

        CombatResult result = dispelDebuffs(
                wearer.statuses().toArray(StatusInstance[]::new));

        assertThat(hero(result).statuses())
                .extracting(StatusInstance::statusType)
                .contains("Fragile");
    }

    @Test
    void keepsTheWeaknessCathedralKnellOpensWith() {
        CombatantState wearer = new fr.insastronaute.webapi.sts.combat.engine.relic.handlers
                .DreadBellRelic()
                .onCombatStart(null, bare(), new java.util.ArrayList<>());

        CombatResult result = dispelDebuffs(
                wearer.statuses().toArray(StatusInstance[]::new));

        assertThat(hero(result).statuses())
                .extracting(StatusInstance::statusType)
                .contains("Weakness");
    }

    private static CombatantState bare() {
        return new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult dispelDebuffs(StatusInstance... carried) {
        CardDefinition cleanse = new CardDefinition(
                "played-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.DISPEL_DEBUFF, -1, true, null, 0, "", null)));
        CombatState state = state(carried);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(cleanse.id(), cleanse)),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID, state.revision(), HERO, "played", List.of(HERO)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state(StatusInstance... carried) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(
                        List.of(),
                        List.of(new CardInstance("played", "played-card")),
                        List.of(),
                        List.of()),
                List.of(carried),
                List.of(),
                null,
                0);
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 100, 1)),
                HERO);
    }
}
