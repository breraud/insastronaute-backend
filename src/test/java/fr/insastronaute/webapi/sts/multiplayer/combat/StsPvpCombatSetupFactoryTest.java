package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StsPvpCombatSetupFactoryTest {

    private static final UUID COMBAT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_USER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void buildsTwoHumanCombatantsOnOpposingTeams() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(
                COMBAT_ID,
                "2026.1.0",
                42L,
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(FIRST_USER, List.of("card-a", "card-b")),
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(SECOND_USER, List.of("card-c")));

        assertThat(setup.combatId()).isEqualTo(COMBAT_ID);
        assertThat(setup.dataVersion()).isEqualTo("2026.1.0");
        assertThat(setup.seed()).isEqualTo(42L);
        assertThat(setup.combatants()).hasSize(2);

        CombatantSetup first = setup.combatants().get(0);
        assertThat(first.combatantId()).isEqualTo(FIRST_USER.toString());
        assertThat(first.teamId()).isEqualTo("team-0");
        assertThat(first.controllerType()).isEqualTo(ControllerType.HUMAN);
        assertThat(first.maxHp()).isEqualTo(80);
        assertThat(first.baseEnergy()).isEqualTo(3);
        assertThat(first.turnDelayTicks()).isEqualTo(25);
        assertThat(first.initialReadyAtTick()).isZero();
        assertThat(first.relics()).isEmpty();
        assertThat(first.deck()).hasSize(2);
        assertThat(first.deck().get(0).definitionId()).isEqualTo("card-a");

        CombatantSetup second = setup.combatants().get(1);
        assertThat(second.combatantId()).isEqualTo(SECOND_USER.toString());
        assertThat(second.teamId()).isEqualTo("team-1");
        assertThat(second.controllerType()).isEqualTo(ControllerType.HUMAN);
        assertThat(second.deck()).hasSize(1);
    }

    @Test
    void givesEveryCardInstanceItsOwnIdentity() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(
                COMBAT_ID,
                "2026.1.0",
                1L,
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(FIRST_USER, List.of("card-a", "card-a")),
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(SECOND_USER, List.of("card-a")));

        List<String> instanceIds = setup.combatants().stream()
                .flatMap(combatant -> combatant.deck().stream())
                .map(card -> card.instanceId())
                .toList();

        assertThat(instanceIds).doesNotHaveDuplicates();
    }

    @Test
    void rejectsAnEmptyDeck() {
        assertThatThrownBy(() -> StsPvpCombatSetupFactory.create(
                COMBAT_ID,
                "2026.1.0",
                1L,
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(FIRST_USER, List.of()),
                new StsPvpCombatSetupFactory.StsPvpParticipantSetup(SECOND_USER, List.of("card-c"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deck");
    }
}
