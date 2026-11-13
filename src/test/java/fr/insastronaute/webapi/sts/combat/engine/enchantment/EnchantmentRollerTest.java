package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnchantmentRollerTest {

    private static final CardDefinition ATTACK = new CardDefinition(
            "frappe", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
            List.of(new EffectDefinition(EffectType.DAMAGE, 6)));
    private static final CardDefinition GUARD = new CardDefinition(
            "garde", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
            List.of(new EffectDefinition(EffectType.ARMOR, 5, true)));

    @Test
    void offersAttackEnchantmentsOnlyToAttacks() {
        assertThat(names(EnchantmentCatalog.candidatesFor(instance(), ATTACK)))
                .contains("Power", "Lifesteal", "Transpercement");
        assertThat(names(EnchantmentCatalog.candidatesFor(instance(), GUARD)))
                .doesNotContain("Power", "Lifesteal", "Transpercement");
    }

    @Test
    void offersProtectionOnlyToACardThatGrantsArmour() {
        assertThat(names(EnchantmentCatalog.candidatesFor(instance(), GUARD))).contains("Protection");
        assertThat(names(EnchantmentCatalog.candidatesFor(instance(), ATTACK))).doesNotContain("Protection");
    }

    @Test
    void neverOffersAnEnchantmentThatWouldClashWithOneTheCardHolds() {
        CardInstance mechanical = new CardInstance(
                "card-1", "frappe", List.of(new EnchantmentInstance("Mécanique", 1)));

        assertThat(names(EnchantmentCatalog.candidatesFor(mechanical, ATTACK)))
                .doesNotContain("Mécanique", "Infinity");
    }

    @Test
    void spendsTheWholeBudgetAndNeverPassesAnEnchantmentCeiling() {
        EnchantmentRoller.Roll roll = EnchantmentRoller.roll(instance(), ATTACK, 12, new RandomState(7));

        assertThat(roll.applied()).isNotEmpty();
        assertThat(roll.applied().stream().mapToInt(EnchantmentInstance::level).sum()).isEqualTo(12);
        assertThat(roll.instance().enchantments()).allSatisfy(enchantment -> {
            int ceiling = EnchantmentCatalog.candidatesFor(instance(), ATTACK).stream()
                    .filter(candidate -> candidate.name().equals(enchantment.enchantmentId()))
                    .mapToInt(EnchantmentCatalog.Candidate::maxLevel)
                    .findFirst()
                    .orElse(Integer.MAX_VALUE);
            assertThat(enchantment.level()).isLessThanOrEqualTo(ceiling);
        });
    }

    @Test
    void stacksTheLevelsOfAnEnchantmentTheCardAlreadyHas() {
        CardInstance sharpened = new CardInstance(
                "card-1", "frappe", List.of(new EnchantmentInstance("Power", 2)));

        EnchantmentRoller.Roll roll = EnchantmentRoller.roll(sharpened, ATTACK, 3, new RandomState(1));

        assertThat(roll.instance().enchantments())
                .extracting(EnchantmentInstance::enchantmentId)
                .doesNotHaveDuplicates();
        assertThat(EnchantmentCatalog.levelOf(roll.instance(), "Power")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void keepsTheCardIdentityAndReplaysIdentically() {
        EnchantmentRoller.Roll first = EnchantmentRoller.roll(instance(), ATTACK, new RandomState(99));
        EnchantmentRoller.Roll second = EnchantmentRoller.roll(instance(), ATTACK, new RandomState(99));

        assertThat(first.instance()).isEqualTo(second.instance());
        assertThat(first.random()).isEqualTo(second.random());
        assertThat(first.instance().instanceId()).isEqualTo("card-1");
        assertThat(first.instance().definitionId()).isEqualTo("frappe");
    }

    @Test
    void appliesNothingWhenNoEnchantmentFitsTheCard() {
        CardDefinition unenchantable = new CardDefinition(
                "rouille", "Rouille", "", 0, CardType.POWER, TargetingMode.NONE,
                Set.of(CardTag.INFINITE), List.of());
        CardInstance saturated = new CardInstance("card-1", "rouille", List.of(
                new EnchantmentInstance("Mécanique", 1),
                new EnchantmentInstance("Stellaire", 1),
                new EnchantmentInstance("Légèreté", 100)));

        EnchantmentRoller.Roll roll =
                EnchantmentRoller.roll(saturated, unenchantable, 5, new RandomState(3));

        assertThat(roll.applied()).isEmpty();
        assertThat(roll.instance()).isEqualTo(saturated);
    }

    private static List<String> names(List<EnchantmentCatalog.Candidate> candidates) {
        return candidates.stream().map(EnchantmentCatalog.Candidate::name).toList();
    }

    private static CardInstance instance() {
        return new CardInstance("card-1", "frappe");
    }
}
