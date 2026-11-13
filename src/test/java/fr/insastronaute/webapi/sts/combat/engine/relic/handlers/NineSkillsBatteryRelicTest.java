package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Seau d'eau : « Toutes les 9 Compétences jouées, gagnez 1 énergie. » */
class NineSkillsBatteryRelicTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final CardDefinition SKILL = new CardDefinition("skill", 0, CardType.SKILL,
            TargetingMode.SELF, Set.of(), List.of(new EffectDefinition(EffectType.ARMOR, 1)));
    private static final CardDefinition ATTACK = new CardDefinition("attack", 0, CardType.ATTACK,
            TargetingMode.SINGLE_ENEMY, Set.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 1)));

    private final NineSkillsBatteryRelic handler = new NineSkillsBatteryRelic();

    @Test
    void theOpeningOfACombatGrantsNothing() {
        CombatantState owner = owner();

        assertThat(handler.onCombatStart(null, owner, new ArrayList<>()).energy()).isEqualTo(3);
    }

    @Test
    void theNinthSkillGrantsOneEnergyAndTheCountStartsOver() {
        CombatantState owner = owner();
        List<Integer> energyAfterEachSkill = new ArrayList<>();
        int gains = 0;
        for (int played = 0; played < 18; played++) {
            ResolutionStep step = play(owner, SKILL);
            owner = step.combatants().getFirst();
            energyAfterEachSkill.add(owner.energy());
            gains += (int) step.events().stream().filter(EnergyGained.class::isInstance).count();
        }

        assertThat(energyAfterEachSkill.get(7)).isEqualTo(3);
        assertThat(energyAfterEachSkill.get(8)).isEqualTo(4);
        assertThat(energyAfterEachSkill.get(16)).isEqualTo(4);
        assertThat(energyAfterEachSkill.get(17)).isEqualTo(5);
        assertThat(gains).isEqualTo(2);
    }

    @Test
    void onlySkillsCount() {
        CombatantState owner = owner();
        for (int played = 0; played < 20; played++) {
            owner = play(owner, ATTACK).combatants().getFirst();
        }

        assertThat(owner.energy()).isEqualTo(3);
        assertThat(owner.recall(handler.id(), NineSkillsBatteryRelic.SKILLS_PLAYED)).isZero();
    }

    /** Unity keeps the count on the relic, which lives the whole run. */
    @Test
    void theCountIsCarriedFromOneCombatToTheNext() {
        assertThat(handler.runScopedMemory())
                .containsEntry(NineSkillsBatteryRelic.SKILLS_PLAYED, 0);
    }

    private ResolutionStep play(CombatantState owner, CardDefinition card) {
        return handler.onCardPlayed(owner, card, CombatCounters.initial(), List.of(owner), List.of(),
                new RandomState(1), COMBAT_ID, 1, new ArrayList<>());
    }

    private static CombatantState owner() {
        return new CombatantState("hero", "hero-team", ControllerType.HUMAN, 50, 50, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), List.of(),
                List.of("NineSkillsBatteryRelic"));
    }
}
