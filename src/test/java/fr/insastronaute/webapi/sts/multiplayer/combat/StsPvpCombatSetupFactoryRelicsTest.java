package fr.insastronaute.webapi.sts.multiplayer.combat;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatSetupFactory.StsPvpParticipantSetup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a duelist brings into a battle besides their deck.
 *
 * <p>Duels used to grant no relics at all, which made every base relic a PvE-only affair: a deck
 * built around what its character's relic does played, in a duel, as a deck built around nothing.
 * Each duelist now carries that relic, named the way a run names it at character select.
 */
class StsPvpCombatSetupFactoryRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000733");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void eachDuelistCarriesTheBaseRelicOfTheirCharacter() {
        CombatSetup setup = create("ITI", "MECA");

        assertThat(setup.combatants().get(0).relics()).containsExactly("ITIRelic");
        assertThat(setup.combatants().get(1).relics()).containsExactly("MECARelic");
    }

    /** The same {@code <character>Relic} name a run writes into its starting inventory. */
    @Test
    void namesTheRelicTheWayARunNamesIt() {
        assertThat(create("EP", "GC").combatants().get(0).relics()).containsExactly("EPRelic");
    }

    @Test
    void aDuelistWithNoCharacterCarriesNothing() {
        CombatSetup setup = create(null, "   ");

        assertThat(setup.combatants().get(0).relics()).isEmpty();
        assertThat(setup.combatants().get(1).relics()).isEmpty();
    }

    private static CombatSetup create(String firstCharacter, String secondCharacter) {
        return StsPvpCombatSetupFactory.create(
                COMBAT_ID,
                "v1",
                42L,
                new StsPvpParticipantSetup(FIRST, List.of("carte"), firstCharacter),
                new StsPvpParticipantSetup(SECOND, List.of("carte"), secondCharacter));
    }
}
