package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatSetupFactory.StsPvpParticipantSetup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le montage d'un combat multijoueur a plus de deux combattants.
 *
 * <p>Le moteur etait deja generique — {@code CombatSetup} prend une liste de combattants portant
 * chacun son equipe et son pilote, et ne compte jamais les joueurs. Ce qui l'empechait de servir
 * etait la couche PVP, qui n'assemblait jamais que deux participants. C'est cet assemblage-la qui
 * est verifie ici.
 */
class StsPvpTeamSetupTest {

    private static final UUID COMBAT = UUID.fromString("0e5f9a2c-0000-4000-8000-000000000001");
    private static final List<String> DECK = List.of("strike", "strike", "defend");

    @Test
    void seatsFourPlayersAcrossTwoTeams() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(COMBAT, "v1", 7L, List.of(
                human("a", 0), human("b", 0), human("c", 1), human("d", 1)));

        assertThat(setup.combatants()).hasSize(4);
        assertThat(setup.combatants()).extracting(CombatantSetup::teamId)
                .containsExactly("team-0", "team-0", "team-1", "team-1");
        assertThat(setup.combatants()).allSatisfy(combatant ->
                assertThat(combatant.controllerType()).isEqualTo(ControllerType.HUMAN));
    }

    /**
     * Un renfort est un combattant comme un autre, avec un vrai deck : c'est ce qui le distingue
     * d'un boss, et ce qui permet a {@code SingleCardEnemyPolicy} de le jouer.
     */
    @Test
    void seatsAnAiAllyWithARealDeck() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(COMBAT, "v1", 7L, List.of(
                human("a", 0), ai("bot", 0, DECK), human("c", 1), human("d", 1)));

        CombatantSetup bot = setup.combatants().get(1);
        assertThat(bot.controllerType()).isEqualTo(ControllerType.AI);
        assertThat(bot.teamId()).isEqualTo("team-0");
        assertThat(bot.deck()).hasSize(DECK.size());
    }

    /**
     * Un boss n'a pas de pioche : il joue le motif de sa definition. Exiger un deck de lui, comme
     * le faisait le montage d'origine de tout participant, aurait refuse le raid tout entier.
     */
    @Test
    void seatsABossWithNoDeckAtAll() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(COMBAT, "v1", 7L, List.of(
                human("a", 0), human("b", 0), boss("raid-boss", 1, 240)));

        CombatantSetup bossSetup = setup.combatants().get(2);
        assertThat(bossSetup.controllerType()).isEqualTo(ControllerType.AI);
        assertThat(bossSetup.teamId()).isEqualTo("team-1");
        assertThat(bossSetup.deck()).isEmpty();
        assertThat(bossSetup.maxHp()).isEqualTo(240);
    }

    /** Un joueur, lui, doit avoir un deck : sans cartes il ne pourrait rien jouer de son tour. */
    @Test
    void stillRefusesAPlayerWithoutADeck() {
        assertThatThrownBy(() -> StsPvpCombatSetupFactory.create(COMBAT, "v1", 7L, List.of(
                new StsPvpParticipantSetup("a", List.of(), "EP", 0, ControllerType.HUMAN, 80),
                human("b", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty deck");
    }

    /** Les duellistes gardent la relique de base de leur personnage, quel que soit le format. */
    @Test
    void everyPlayerKeepsTheirCharacterRelic() {
        CombatSetup setup = StsPvpCombatSetupFactory.create(COMBAT, "v1", 7L, List.of(
                human("a", 0), human("b", 0), human("c", 1), human("d", 1)));

        assertThat(setup.combatants()).allSatisfy(combatant ->
                assertThat(combatant.relics()).containsExactly("EPRelic"));
    }

    private static StsPvpParticipantSetup human(String combatantId, int teamIndex) {
        return new StsPvpParticipantSetup(
                combatantId, DECK, "EP", teamIndex, ControllerType.HUMAN,
                StsPvpCombatSetupFactory.PVP_MAX_HP);
    }

    private static StsPvpParticipantSetup ai(String combatantId, int teamIndex, List<String> deck) {
        return new StsPvpParticipantSetup(
                combatantId, deck, "EP", teamIndex, ControllerType.AI,
                StsPvpCombatSetupFactory.PVP_MAX_HP);
    }

    private static StsPvpParticipantSetup boss(String combatantId, int teamIndex, int maxHp) {
        return new StsPvpParticipantSetup(
                combatantId, List.of(), null, teamIndex, ControllerType.AI, maxHp);
    }
}
