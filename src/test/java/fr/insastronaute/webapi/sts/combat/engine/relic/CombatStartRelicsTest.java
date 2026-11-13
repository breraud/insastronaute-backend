package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.BlackSunRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GlassCannonRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.InfernalContractRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.LedgerRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.LeviathanCoreRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.RunicAegisRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SolarCageRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SovereignHaloRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.TitanHeartRelic;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The nine boss/common relics that fire once, at combat start, and stay quiet after.
 *
 * <p>Every one of them is exercised through {@link CombatFactory}, the only path that actually
 * triggers {@code onCombatStart} in the engine: a handler called directly proves nothing about a
 * relic actually firing in play.
 *
 * <p>Several of these read {@code OnTurnStart} in Unity rather than {@code OnCombatStart}, purely
 * so the grant would survive Unity's own start-of-turn energy/armour reset. The Java engine does
 * not run that reset (nor call {@code onTurnStart}) for the very first turn of a combat — {@link
 * CombatFactory} sets the opening energy/hand once and then calls {@code onCombatStart} directly
 * — so granting the same numbers there reproduces the intended one-shot effect faithfully for the
 * combatant who acts first, without needing a "have I fired yet" memory flag at all.
 */
class CombatStartRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000131");

    @Test
    void blackSunGrantsStrengthDexterityWeaknessAndVulnAtCombatStart() {
        CombatantState hero = create("BlackSunRelic");

        assertThat(hero.statuses()).containsExactlyInAnyOrder(
                new StatusInstance("Strength", 3, -1, "", 0),
                new StatusInstance("Dexterity", 3, -1, "", 0),
                new StatusInstance("Weakness", 0, 1, "", 0),
                new StatusInstance("Vuln", 0, 1, "", 0));
    }

    @Test
    void infernalContractGrantsEnergyDrawsTwoAndAppliesPoisonAtCombatStart() {
        CombatantState hero = create("InfernalContractRelic");

        // Unity's InfernalContractRelic.OnCombatStart applies
        // StatusEffect.Factory(StatusType.Poison, 6, 6): Poison reads only the duration
        // (PoisonStatus(duration)), so this is 6 stacks of Poison, not the 4 the description
        // advertises. Followed as written in the C#.
        assertThat(hero.energy()).isEqualTo(5);
        assertThat(hero.piles().hand()).hasSize(7);
        assertThat(hero.statuses())
                .anySatisfy(status -> {
                    assertThat(status.statusType()).isEqualTo("Poison");
                    assertThat(status.duration()).isEqualTo(6);
                });
    }

    @Test
    void leviathanCoreGrantsEnergyAndStrengthAtCombatStart() {
        CombatantState hero = create("LeviathanCoreRelic");

        assertThat(hero.energy()).isEqualTo(5);
        assertThat(hero.statuses()).containsExactly(new StatusInstance("Strength", 1, -1, "", 0));
    }

    @Test
    void runicAegisGrantsArmorArtifactAndReducesStartingEnergyAtCombatStart() {
        CombatantState hero = create("RunicAegisRelic");

        assertThat(hero.armor()).isEqualTo(15);
        assertThat(hero.energy()).isEqualTo(2);
        assertThat(hero.statuses()).containsExactly(new StatusInstance("Artifact", 1, -1, "", 0));
    }

    @Test
    void solarCageGrantsEnergyAndDrawsThreeAtCombatStart() {
        CombatantState hero = create("SolarCageRelic");

        assertThat(hero.energy()).isEqualTo(4);
        assertThat(hero.piles().hand()).hasSize(8);
    }

    @Test
    void sovereignHaloGrantsArtifactAtCombatStart() {
        CombatantState hero = create("SovereignHaloRelic");

        assertThat(hero.statuses()).containsExactly(new StatusInstance("Artifact", 3, -1, "", 0));
    }

    @Test
    void titanHeartGrantsArmorStrengthDexterityAndFragileAtCombatStart() {
        CombatantState hero = create("TitanHeartRelic");

        assertThat(hero.armor()).isEqualTo(20);
        assertThat(hero.statuses()).containsExactlyInAnyOrder(
                new StatusInstance("Strength", 1, -1, "", 0),
                new StatusInstance("Dexterity", 1, -1, "", 0),
                // The price of the relic is meant to stick: this Fragile is the instance Unity
                // marks as unremovable, not an ordinary one.
                StatusInstance.unremovable("Fragile", 0, 99));
    }

    @Test
    void glassCannonGrantsSpeedAtCombatStart() {
        CombatantState hero = create("GlassCannonRelic");

        assertThat(hero.statuses()).containsExactly(new StatusInstance("Speed", 5, -1, "", 0));
    }

    @Test
    void ledgerDrawsOneCardAtCombatStart() {
        CombatantState hero = create("LedgerRelic");

        assertThat(hero.piles().hand()).hasSize(6);
    }

    private static CombatantState create(String relicId) {
        RelicEngine relicEngine = new RelicEngine(List.of(
                new BlackSunRelic(),
                new InfernalContractRelic(),
                new LeviathanCoreRelic(),
                new RunicAegisRelic(),
                new SolarCageRelic(),
                new SovereignHaloRelic(),
                new TitanHeartRelic(),
                new GlassCannonRelic(),
                new LedgerRelic()));
        CardCatalog catalog = new CardCatalog("v1", Map.of("strike", strike()));
        CombatFactory factory = new CombatFactory(catalog, relicEngine);

        CombatantSetup hero = new CombatantSetup(
                "hero", "players", ControllerType.HUMAN, 80, 3, 10, 0,
                deck("hero", 20), List.of(relicId));
        CombatantSetup enemy = new CombatantSetup(
                "enemy", "enemies", ControllerType.AI, 30, 2, 12, 100, List.of());

        CombatSetup setup = new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, enemy));
        CombatCreated created = factory.create(setup);
        CombatState state = created.state();
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    private static List<CardInstance> deck(String prefix, int size) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            deck.add(new CardInstance(prefix + "-" + index, "strike"));
        }
        return deck;
    }

    private static CardDefinition strike() {
        return new CardDefinition(
                "strike", 1, fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, java.util.Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, "", null)));
    }
}
