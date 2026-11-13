package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.HumanismEnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.LifestealEnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.TelekinesisEnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineVigorAndLifestealTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String CARD = "tested-card";
    private static final int PERMANENT = -1;

    @Test
    void vigorAddsItsValueToTheDamageDealt() {
        CombatResult result = play(
                attack(8, java.util.Set.of()),
                List.of(new StatusInstance("Vigor", 5, PERMANENT, null, 0)),
                List.of());

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(7);
    }

    @Test
    void vigorDissipatesOnceADamageDealingCardIsPlayed() {
        CombatResult result = play(
                attack(8, java.util.Set.of()),
                List.of(new StatusInstance("Vigor", 5, PERMANENT, null, 0)),
                List.of());

        assertThat(statusValue(combatant(result, HERO), "Vigor")).isNull();
    }

    /** Une carte qui n'inflige rien n'a pas consomme la Vigueur : elle attend la suivante. */
    @Test
    void vigorSurvivesACardThatDealsNoDamage() {
        CardDefinition block = new CardDefinition(
                CARD, 1, TargetingMode.SELF, List.of(new EffectDefinition(EffectType.ARMOR, 5)));

        CombatResult result = play(
                block, List.of(new StatusInstance("Vigor", 5, PERMANENT, null, 0)), List.of(), HERO);

        assertThat(statusValue(combatant(result, HERO), "Vigor")).isEqualTo(5);
    }

    @Test
    void lifestealHealsAShareOfTheDamageDealt() {
        // Niveau 3 : 15% de 8 degats, soit 1 point rendu.
        CombatResult result = play(
                attack(8, java.util.Set.of()),
                List.of(),
                List.of(new EnchantmentInstance("Lifesteal", 3)));

        assertThat(combatant(result, HERO).hp()).isEqualTo(21);
    }

    @Test
    void lifestealCountsTheDamageAfterVigorRaisedIt() {
        // 8 + 5 de Vigueur = 13 infliges, dont 50% rendus au niveau 10.
        CombatResult result = play(
                attack(8, java.util.Set.of()),
                List.of(new StatusInstance("Vigor", 5, PERMANENT, null, 0)),
                List.of(new EnchantmentInstance("Lifesteal", 10)));

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(7);
        assertThat(combatant(result, HERO).hp()).isEqualTo(26);
    }

    @Test
    void aCardWithoutTheEnchantmentStealsNothing() {
        CombatResult result = play(attack(8, java.util.Set.of()), List.of(), List.of());

        assertThat(combatant(result, HERO).hp()).isEqualTo(20);
    }

        @Test
        void humanismeMakesDamageIgnoreArmor() {
                CombatResult result = playAgainst(
                                attack(8, java.util.Set.of()), List.of(new EnchantmentInstance("Humanisme", 1)),
                                5, List.of());

                assertThat(combatant(result, ENEMY).hp()).isEqualTo(12);
                assertThat(combatant(result, ENEMY).armor()).isEqualTo(5);
        }

        @Test
        void telekinesisFloorsReducedDamageAtItsBaseValue() {
                CombatResult result = playAgainst(
                                attack(8, java.util.Set.of()), List.of(new EnchantmentInstance("Télékinésie", 1)),
                                0, List.of(new StatusInstance("DamageReduction", 50, PERMANENT, null, 0)));

                assertThat(combatant(result, ENEMY).hp()).isEqualTo(12);
        }

    /** Un suivi ne recoit pas la Vigueur, donc ne la consomme pas non plus. */
    @Test
    void aFollowUpNeitherUsesNorSpendsVigor() {
        CombatResult result = play(
                attack(8, java.util.Set.of(CardTag.FOLLOW_UP)),
                List.of(new StatusInstance("Vigor", 5, PERMANENT, null, 0)),
                List.of());

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(12);
        assertThat(statusValue(combatant(result, HERO), "Vigor")).isEqualTo(5);
    }

    private static CardDefinition attack(int damage, java.util.Set<CardTag> tags) {
        return new CardDefinition(
                CARD,
                1,
                CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY,
                tags,
                List.of(new EffectDefinition(EffectType.DAMAGE, damage)));
    }

    private static CombatResult play(
            CardDefinition definition,
            List<StatusInstance> heroStatuses,
            List<EnchantmentInstance> enchantments) {
        return play(definition, heroStatuses, enchantments, ENEMY);
    }

    private static CombatResult play(
            CardDefinition definition,
            List<StatusInstance> heroStatuses,
            List<EnchantmentInstance> enchantments,
            String targetId) {
        return playAgainst(definition, enchantments, 0, List.of(), heroStatuses, targetId);
    }

    private static CombatResult playAgainst(
            CardDefinition definition,
            List<EnchantmentInstance> enchantments,
            int enemyArmor,
            List<StatusInstance> enemyStatuses) {
        return playAgainst(definition, enchantments, enemyArmor, enemyStatuses, List.of(), ENEMY);
    }

    private static CombatResult playAgainst(
            CardDefinition definition,
            List<EnchantmentInstance> enchantments,
            int enemyArmor,
            List<StatusInstance> enemyStatuses,
            List<StatusInstance> heroStatuses,
            String targetId) {
        CardInstance card = new CardInstance(
                "played", CARD, enchantments, null, null, List.of(), List.of(), List.of());
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()),
                new EnchantmentEngine(List.of(
                        new LifestealEnchantmentHandler(),
                        new HumanismEnchantmentHandler(),
                        new TelekinesisEnchantmentHandler())));
        return engine.handle(
                state(card, heroStatuses, enemyArmor, enemyStatuses),
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(), List.of(targetId)));
    }

    private static CombatState state(CardInstance card, List<StatusInstance> heroStatuses) {
        return state(card, heroStatuses, 0, List.of());
    }

    private static CombatState state(
            CardInstance card,
            List<StatusInstance> heroStatuses,
            int enemyArmor,
            List<StatusInstance> enemyStatuses) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 20, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                heroStatuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 20, 20, enemyArmor, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), enemyStatuses);
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 10), new TimelineEntry(ENEMY, 1, 12)),
                HERO);
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static Integer statusValue(CombatantState combatant, String statusType) {
        return combatant.statuses().stream()
                .filter(status -> status.statusType().equals(statusType))
                .map(StatusInstance::value)
                .findFirst()
                .orElse(null);
    }
}
