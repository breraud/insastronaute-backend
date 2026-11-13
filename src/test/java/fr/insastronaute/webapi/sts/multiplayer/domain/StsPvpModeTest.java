package fr.insastronaute.webapi.sts.multiplayer.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les formats du multijoueur.
 *
 * <p>Ce qui compte ici est la repartition des sieges : c'est elle qui decide de quel cote tombe
 * chaque joueur admis, et un raid ou les deux joueurs se retrouveraient face a face serait un
 * bug qu'aucune autre couche ne rattraperait.
 */
class StsPvpModeTest {

    @Test
    void aDuelSeatsOnePlayerPerTeam() {
        assertThat(StsPvpMode.ONE_V_ONE.totalHumanSeats()).isEqualTo(2);
        assertThat(StsPvpMode.ONE_V_ONE.teamOfHumanSeat(0)).isZero();
        assertThat(StsPvpMode.ONE_V_ONE.teamOfHumanSeat(1)).isEqualTo(1);
        assertThat(StsPvpMode.ONE_V_ONE.hasBoss()).isFalse();
    }

    /** Deux contre deux : les sieges se remplissent equipe par equipe, pas en alternance. */
    @Test
    void aTwoVersusTwoFillsTheFirstTeamBeforeTheSecond() {
        assertThat(StsPvpMode.TWO_V_TWO.totalHumanSeats()).isEqualTo(4);
        assertThat(StsPvpMode.TWO_V_TWO.teamOfHumanSeat(0)).isZero();
        assertThat(StsPvpMode.TWO_V_TWO.teamOfHumanSeat(1)).isZero();
        assertThat(StsPvpMode.TWO_V_TWO.teamOfHumanSeat(2)).isEqualTo(1);
        assertThat(StsPvpMode.TWO_V_TWO.teamOfHumanSeat(3)).isEqualTo(1);
    }

    /**
     * Un raid met ses deux joueurs du <em>meme</em> cote, et le boss seul en face. C'est la seule
     * chose qui distingue un raid d'un 2v2 aux yeux du montage du combat.
     */
    @Test
    void aRaidPutsBothPlayersOnTheSameSideAndTheBossOnTheOther() {
        assertThat(StsPvpMode.RAID.totalHumanSeats()).isEqualTo(2);
        assertThat(StsPvpMode.RAID.teamOfHumanSeat(0)).isZero();
        assertThat(StsPvpMode.RAID.teamOfHumanSeat(1)).isZero();
        assertThat(StsPvpMode.RAID.humanSeats(1)).isZero();
        assertThat(StsPvpMode.RAID.hasBoss()).isTrue();
        assertThat(StsPvpMode.RAID.bossTeamIndex()).isEqualTo(1);
    }

    /**
     * Un client ecrit avant les modes n'envoie pas le champ, et il demande bien un duel : c'est
     * aussi ce que valent toutes les batailles et toutes les entrees de file deja en base.
     */
    @Test
    void anAbsentModeIsTheDuelEverythingUsedToBe() {
        assertThat(StsPvpMode.parse(null)).isEqualTo(StsPvpMode.ONE_V_ONE);
        assertThat(StsPvpMode.parse("  ")).isEqualTo(StsPvpMode.ONE_V_ONE);
        assertThat(StsPvpMode.ofNullable(null)).isEqualTo(StsPvpMode.ONE_V_ONE);
    }

    @Test
    void readsAModeWhateverItsCasingOrSeparator() {
        assertThat(StsPvpMode.parse("two_v_two")).isEqualTo(StsPvpMode.TWO_V_TWO);
        assertThat(StsPvpMode.parse("TWO-V-TWO")).isEqualTo(StsPvpMode.TWO_V_TWO);
        assertThat(StsPvpMode.parse(" raid ")).isEqualTo(StsPvpMode.RAID);
        assertThat(StsPvpMode.parse("2v2")).isEqualTo(StsPvpMode.TWO_V_TWO);
    }

    /**
     * Un mode inconnu est refuse et non ramene au duel : le demandeur a nomme un format, et le
     * mettre silencieusement dans un autre est pire que lui dire non.
     */
    @Test
    void refusesAModeItDoesNotKnowRatherThanGuessing() {
        assertThatThrownBy(() -> StsPvpMode.parse("THREE_V_THREE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("THREE_V_THREE");
    }

    @Test
    void refusesASeatThatDoesNotExist() {
        assertThatThrownBy(() -> StsPvpMode.RAID.teamOfHumanSeat(2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
