package fr.insastronaute.webapi.sts.combat.engine.relic;

import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.FieldRationsRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.RestChargesRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.RestHealRelicHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le portage des reliques de repos est la partie silencieuse du feu de camp
 * autoritatif : une erreur ne casse rien de visible, le joueur perd juste un
 * effet. D'où des tests qui portent sur les nombres, repris littéralement du
 * client Unity.
 */
class RestSiteRelicsTest {

    private final RelicEngine engine = new RelicEngine(List.of(
            new RestChargesRelicHandler(),
            new RestHealRelicHandler(),
            new FieldRationsRelicHandler()
    ));

    @Test
    void experienceGrantsOneMoreCharge() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("RestChargesRelic"), new RelicHandler.RestContext(30, 80, 3));

        assertThat(outcome.charges()).isEqualTo(4);
        assertThat(outcome.currentHp()).isEqualTo(30);
    }

    @Test
    void theMassageChairHealsFive() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("RestHealRelic"), new RelicHandler.RestContext(30, 80, 3));

        assertThat(outcome.currentHp()).isEqualTo(35);
        assertThat(outcome.charges()).isEqualTo(3);
    }

    @Test
    void fieldRationsHealFour() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("FieldRationsRelic"), new RelicHandler.RestContext(30, 80, 3));

        assertThat(outcome.currentHp()).isEqualTo(34);
    }

    @Test
    void healingRelicsStopAtFullHealth() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("RestHealRelic", "FieldRationsRelic"), new RelicHandler.RestContext(78, 80, 3));

        assertThat(outcome.currentHp()).isEqualTo(80);
    }

    @Test
    void eachRelicSeesWhatThePreviousOneLeft() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("RestHealRelic", "FieldRationsRelic", "RestChargesRelic"),
                new RelicHandler.RestContext(30, 80, 3));

        assertThat(outcome.currentHp()).isEqualTo(39);
        assertThat(outcome.charges()).isEqualTo(4);
    }

    /**
     * Une relique que le serveur ne connaît pas ne doit pas interrompre l'entrée :
     * arriver sur un feu de camp inerte serait pire que perdre un effet.
     */
    @Test
    void anUnknownRelicChangesNothing() {
        RelicHandler.RestOutcome outcome = engine.processEnterRestSite(
                List.of("RelicQuiNExistePas"), new RelicHandler.RestContext(30, 80, 3));

        assertThat(outcome.currentHp()).isEqualTo(30);
        assertThat(outcome.charges()).isEqualTo(3);
    }

    /** Les soixante-quatre autres handlers n'ont pas redéfini le crochet. */
    @Test
    void aRelicWithoutTheHookLeavesTheContextIntact() {
        RelicHandler silent = () -> "RelicSansCrochet";

        assertThat(silent.onEnterRestSite(new RelicHandler.RestContext(30, 80, 3)))
                .isEqualTo(new RelicHandler.RestOutcome(30, 3));
    }
}
