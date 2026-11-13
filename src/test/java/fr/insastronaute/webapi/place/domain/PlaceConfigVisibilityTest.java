package fr.insastronaute.webapi.place.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceConfigVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    private static PlaceConfig config(boolean windowOpen, PlaceVisibilityMode mode) {
        PlaceConfig config = new PlaceConfig();
        config.setActive(true);
        config.setStartDate(windowOpen ? NOW.minus(Duration.ofHours(1)) : NOW.plus(Duration.ofDays(1)));
        config.setEndDate(windowOpen ? NOW.plus(Duration.ofHours(1)) : NOW.plus(Duration.ofDays(2)));
        config.setVisibilityMode(mode);
        return config;
    }

    /** Automatique garde le comportement d'avant : l'onglet n'existe que pendant la fenêtre. */
    @Test
    void automaticFollowsTheEventWindow() {
        assertThat(config(true, PlaceVisibilityMode.AUTO).isVisibleAt(NOW)).isTrue();
        assertThat(config(false, PlaceVisibilityMode.AUTO).isVisibleAt(NOW)).isFalse();
    }

    /** Annoncer Place avant l'ouverture : on voit le canevas, on ne pose rien. */
    @Test
    void shownAnnouncesAClosedEventWithoutOpeningIt() {
        PlaceConfig config = config(false, PlaceVisibilityMode.SHOWN);
        assertThat(config.isVisibleAt(NOW)).isTrue();
        assertThat(config.isOpenAt(NOW)).isFalse();
    }

    /**
     * Retirer Place du front doit aussi le fermer : sans cela, un événement masqué
     * resterait posable par quiconque garde le lien.
     */
    @Test
    void hiddenClosesAnOpenEventEvenByDirectLink() {
        PlaceConfig config = config(true, PlaceVisibilityMode.HIDDEN);
        assertThat(config.isVisibleAt(NOW)).isFalse();
        assertThat(config.isOpenAt(NOW)).isFalse();
    }

    @Test
    void anUnsetModeFallsBackToAutomatic() {
        assertThat(config(true, null).getVisibilityMode()).isEqualTo(PlaceVisibilityMode.AUTO);
    }

    /**
     * Les resultats n'existent qu'apres la fin, et seulement si l'admin les allume.
     *
     * <p>Le reglage est independant de {@code active} et du mode de visibilite :
     * l'evenement termine se range — onglet retire, canevas ferme — sans que la
     * page des resultats disparaisse avec lui.</p>
     */
    @Test
    void resultsNeedBothTheEndOfTheWindowAndTheAdminSwitch() {
        PlaceConfig before = config(true, PlaceVisibilityMode.AUTO);
        before.setResultsVisible(true);
        assertThat(before.showsResultsAt(NOW)).isFalse();

        PlaceConfig after = config(true, PlaceVisibilityMode.AUTO);
        after.setResultsVisible(true);
        assertThat(after.showsResultsAt(NOW.plus(Duration.ofHours(2)))).isTrue();

        PlaceConfig switchedOff = config(true, PlaceVisibilityMode.AUTO);
        assertThat(switchedOff.showsResultsAt(NOW.plus(Duration.ofHours(2)))).isFalse();
    }

    /** Ranger l'evenement ne doit pas emporter ses resultats. */
    @Test
    void resultsSurviveHidingAndDeactivatingTheEvent() {
        PlaceConfig config = config(true, PlaceVisibilityMode.HIDDEN);
        config.setActive(false);
        config.setResultsVisible(true);
        assertThat(config.showsResultsAt(NOW.plus(Duration.ofHours(2)))).isTrue();
    }

    /** Sans date de fin, il n'y a pas de fin : rien a montrer. */
    @Test
    void resultsStayHiddenWithoutAnEndDate() {
        PlaceConfig config = config(true, PlaceVisibilityMode.AUTO);
        config.setEndDate(null);
        config.setResultsVisible(true);
        assertThat(config.showsResultsAt(NOW)).isFalse();
    }
}
