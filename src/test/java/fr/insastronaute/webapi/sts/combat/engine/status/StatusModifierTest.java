package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * How statuses change the numbers of a blow.
 *
 * <p>Five of these were modelled and twenty-two were not, so most of the game's statuses sat in
 * the state doing nothing to the combat they were part of.
 */
class StatusModifierTest {

    private static final CardDefinition CARD = new CardDefinition(
            "frappe", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
            List.of(new EffectDefinition(EffectType.DAMAGE, 10)));

    @Test
    void strengthHelpsWhoeverThrowsTheBlow() {
        assertThat(damage(caster("Strength", 4, -1), plainTarget())).isEqualTo(14);
        assertThat(damage(plainCaster(), target("Strength", 4, -1)))
                .describedAs("and does nothing for whoever takes it")
                .isEqualTo(10);
    }

    @Test
    void weaknessSoftensTheBlowAndVulnHardensIt() {
        assertThat(damage(caster("Weakness", 0, 2), plainTarget())).isEqualTo(7);
        assertThat(damage(plainCaster(), target("Vuln", 0, 2))).isEqualTo(15);
    }

    @Test
    void blindingSomeoneStopsTheirBlowEntirely() {
        assertThat(damage(caster("Blind", 0, 2), plainTarget())).isZero();
    }

    @Test
    void dexterityThickensArmourAndFragilityThinsIt() {
        assertThat(armor(caster("Dexterity", 3, -1))).isEqualTo(13);
        assertThat(armor(caster("Fragile", 0, 2))).isEqualTo(7);
        assertThat(armor(caster("ArmorBlock", 0, 2)))
                .describedAs("and blocking it removes it")
                .isZero();
    }

    @Test
    void hasteShortensTheWaitAndSlowLengthensIt() {
        assertThat(turnDelay(caster("Haste", 0, 2))).isEqualTo(8);
        assertThat(turnDelay(caster("Slow", 0, 2))).isEqualTo(12);
    }

    @Test
    void speedShortensTheWaitMoreSlowlyAsItStacks() {
        int one = turnDelay(caster("Speed", 1, -1));
        int four = turnDelay(caster("Speed", 4, -1));
        int sixteen = turnDelay(caster("Speed", 16, -1));

        assertThat(one).isGreaterThan(four);
        assertThat(four).isGreaterThan(sixteen);
        assertThat(sixteen).isPositive();
    }

    @Test
    void managingRiskCapsHowHardASingleBlowCanBe() {
        assertThat(damage(plainCaster(), target("RiskManagement", 5, -1)))
                .describedAs("a light guard caps at 25, above this blow")
                .isEqualTo(10);
        assertThat(damage(plainCaster(), target("RiskManagement", 25, -1)))
                .describedAs("a heavy guard caps at 5, below it")
                .isEqualTo(5);
    }

    @Test
    void reducingDamageTakesAPercentageOfIt() {
        assertThat(damage(plainCaster(), target("DamageReduction", 40, -1))).isEqualTo(6);
    }

    @Test
    void adrenalineReducesIncomingDamageByHalfTheMissingHealthPercentage() {
        CombatantState wounded = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 15, 0, 2, 2, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Adrenaline", 2, -1, "", 0)));

        assertThat(damage(plainCaster(), wounded)).isEqualTo(8);
    }

    @Test
    void brandingSomeoneScalesWithHowManyWaysTheyAreAlreadyHurt() {
        CombatantState branded = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Brand", 50, 3, "", 0),
                        new StatusInstance("Poison", 0, 2, "", 1),
                        new StatusInstance("Weakness", 0, 2, "", 2)));

        assertThat(damage(plainCaster(), branded))
                .describedAs("the two other debuffs count, the brand itself does not")
                .isEqualTo(20);
    }

    @Test
    void aStatusWithNoNumberToChangeStaysOutOfTheWay() {
        assertThat(damage(caster("Targeting", 1, 2), plainTarget())).isEqualTo(10);
    }

    private static int damage(CombatantState source, CombatantState target) {
        return BattleCalculator.getModifiedValue(10, StatType.DAMAGE, context(source, target));
    }

    private static int armor(CombatantState source) {
        return BattleCalculator.getModifiedValue(10, StatType.ARMOR, context(source, plainTarget()));
    }

    private static int turnDelay(CombatantState source) {
        return BattleCalculator.getModifiedValue(
                10, StatType.TURN_DELAY, context(source, plainTarget()));
    }

    private static EffectContext context(CombatantState source, CombatantState target) {
        return new EffectContext(null, source, target, List.of(target), CARD, false, List.of());
    }

    private static CombatantState plainCaster() {
        return combatant("hero", "players");
    }

    private static CombatantState plainTarget() {
        return combatant("enemy", "enemies");
    }

    private static CombatantState caster(String type, int value, int duration) {
        return withStatus("hero", "players", type, value, duration);
    }

    private static CombatantState target(String type, int value, int duration) {
        return withStatus("enemy", "enemies", type, value, duration);
    }

    private static CombatantState withStatus(
            String id, String team, String type, int value, int duration) {
        return new CombatantState(id, team, ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance(type, value, duration, "", 0)));
    }

    private static CombatantState combatant(String id, String team) {
        return new CombatantState(id, team, ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}
