package fr.insastronaute.webapi.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux configurations mettent le limiteur hors service sans que rien ne le signale.
 *
 * <p>L'application demarre normalement dans les deux cas, et elles ne se voient qu'en
 * essayant de depasser une limite qui n'existe plus — c'est-a-dire jamais, jusqu'au jour
 * ou quelqu'un le fait pour de bon.</p>
 */
class RateLimitStartupReportTest {

    @Test
    void saysSoWhenRedisIsMissingAndNothingIsThrottledAtAll() {
        assertThat(new RateLimitStartupReport(false, "framework").warnings())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("Aucune limitation de debit")
                .contains("passage prioritaire");
    }

    /**
     * Derriere un proxy non declare, {@code getRemoteAddr()} rend l'adresse du proxy.
     *
     * <p>Tous les clients partagent alors un seul compteur : le premier a l'epuiser ferme
     * la route aux autres, et la limite protege l'inverse de ce qu'elle vise.</p>
     */
    @Test
    void saysSoWhenAddressBasedLimitsWouldCountTheProxy() {
        assertThat(new RateLimitStartupReport(true, "none").warnings())
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("adresse du proxy")
                .contains("SERVER_FORWARD_HEADERS_STRATEGY=framework");
    }

    @Test
    void staysSilentWhenBothArePlaceRight() {
        assertThat(new RateLimitStartupReport(true, "framework").warnings()).isEmpty();
        assertThat(new RateLimitStartupReport(true, "native").warnings()).isEmpty();
    }

    /** Sans Redis, l'avertissement sur le proxy n'apprendrait rien : il n'y a plus de limite. */
    @Test
    void reportsTheRootCauseOnlyWhenBothAreWrong() {
        assertThat(new RateLimitStartupReport(false, "none").warnings()).hasSize(1);
    }
}
