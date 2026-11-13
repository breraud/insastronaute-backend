package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Intemporalité allonge la durée, choisit son bord à l'équipe, et vaut donc aussi en duel.
 */
class AIRelicHandlerTest {

    private static final String RELIC = "AIRelic";
    private static final int PERMANENT = -1;

    @Test
    void testId() {
        assertThat(new AIRelicHandler().id()).isEqualTo(RELIC);
    }

    /** La promesse de la relique : des tours en plus, pas des points en plus. */
    @Test
    void itAddsTurnsRatherThanValue() {
        StatusInstance vuln = apply(bearer(), foe(), status("Vuln", 0, 2));

        assertThat(vuln.duration()).isEqualTo(4);
        assertThat(vuln.value())
                .describedAs("Vulnérable ne lit pas sa valeur : la gonfler ne faisait rien")
                .isZero();
    }

    /** En duel les deux camps sont humains : le bord se lit à l'équipe, pas au pilote. */
    @Test
    void aDebuffOnAHumanOpponentIsStillExtended() {
        CombatantState rival = new CombatantState(
                "rival", "rival-team", ControllerType.HUMAN, 80, 80, 0, 3, 3, 25,
                piles(), List.of(), List.of(), null, 0);

        assertThat(apply(bearer(), rival, status("Weakness", 0, 1)).duration()).isEqualTo(3);
    }

    @Test
    void aBuffOnItsOwnBearerIsExtended() {
        CombatantState bearer = bearer();

        assertThat(apply(bearer, bearer, status("Regen", 0, 3)).duration()).isEqualTo(5);
    }

    /** Elle n'allonge pas ce que l'autre bord se donne, ni ce qu'on se donne de mauvais. */
    @Test
    void itLeavesTheOtherSidesBuffsAndItsOwnDebuffsAlone() {
        assertThat(apply(bearer(), foe(), status("Regen", 0, 3)).duration()).isEqualTo(3);
        assertThat(apply(bearer(), bearer(), status("Weakness", 0, 2)).duration()).isEqualTo(2);
    }

    /** Les règles d'ExtendStatuses : rien d'inextensible, rien de permanent. */
    @Test
    void itRefusesWhatHasNoClockToGrow() {
        assertThat(apply(bearer(), foe(), status("Confusion", 0, 1)).duration())
                .describedAs("Confusion est inextensible")
                .isEqualTo(1);
        assertThat(apply(bearer(), bearer(), status("Strength", 3, PERMANENT)).duration())
                .describedAs("un statut permanent n'a pas d'horloge")
                .isEqualTo(PERMANENT);
        assertThat(apply(bearer(), bearer(), status("Strength", 3, PERMANENT)).value())
                .describedAs("et sa valeur ne bouge pas non plus")
                .isEqualTo(3);
    }

    private static StatusInstance apply(
            CombatantState source, CombatantState target, StatusInstance status) {
        return new AIRelicHandler().onStatusApply(source, target, status);
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, null, 0);
    }

    private static CombatantState bearer() {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN, 80, 80, 0, 3, 3, 25,
                piles(), List.of(), List.of(RELIC), null, 0);
    }

    private static CombatantState foe() {
        return new CombatantState(
                "slime", "enemy-team", ControllerType.AI, 40, 40, 0, 0, 0, 25,
                piles(), List.of(), List.of(), null, 0);
    }

    private static CardPiles piles() {
        return new CardPiles(List.of(), List.of(), List.of(), List.of());
    }
}
