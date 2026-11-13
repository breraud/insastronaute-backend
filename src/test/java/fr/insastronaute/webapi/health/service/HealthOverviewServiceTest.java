package fr.insastronaute.webapi.health.service;

import fr.insastronaute.webapi.health.domain.HealthSample;
import fr.insastronaute.webapi.health.dto.HealthPointDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les compteurs HTTP sont conservés cumulatifs, comme Micrometer les tient : c'est à
 * la lecture qu'ils deviennent des taux. Tout ce qui suit porte sur cette conversion,
 * parce que c'est elle qui peut mentir.
 */
class HealthOverviewServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-27T02:00:00Z");

    @Test
    void theFirstPointHasNoRate() {
        // Un taux demande deux points. Rendre zéro laisserait croire à un service muet.
        List<HealthPointDto> points = HealthOverviewService.toRates(List.of(sample(T0, 100, 2)));

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().requestsPerSecond()).isNull();
        assertThat(points.getFirst().errorsPerSecond()).isNull();
    }

    @Test
    void aRateIsTheDifferenceOverTheElapsedTime() {
        List<HealthPointDto> points = HealthOverviewService.toRates(List.of(
                sample(T0, 100, 2),
                sample(T0.plusSeconds(30), 400, 5)
        ));

        assertThat(points.get(1).requestsPerSecond()).isEqualTo(10.0);
        assertThat(points.get(1).errorsPerSecond()).isEqualTo(0.1);
    }

    /**
     * Le cas qui compte. Les compteurs de Micrometer vivent dans la JVM et repartent de
     * zéro à chaque déploiement — il y en a eu une dizaine la nuit où ceci a été écrit.
     * Une différence négative n'est pas un trafic négatif.
     */
    @Test
    void aCounterGoingBackwardsIsARestartAndNotNegativeTraffic() {
        List<HealthPointDto> points = HealthOverviewService.toRates(List.of(
                sample(T0, 5_000, 40),
                sample(T0.plusSeconds(30), 12, 0)
        ));

        assertThat(points.get(1).requestsPerSecond())
                .as("un trou dans la courbe se lit ; une pointe inventée se croit")
                .isNull();
        assertThat(points.get(1).errorsPerSecond()).isNull();
    }

    @Test
    void theSeriesRecoversItsRateAfterTheRestart() {
        List<HealthPointDto> points = HealthOverviewService.toRates(List.of(
                sample(T0, 5_000, 40),
                sample(T0.plusSeconds(30), 12, 0),
                sample(T0.plusSeconds(60), 312, 3)
        ));

        assertThat(points.get(2).requestsPerSecond()).isEqualTo(10.0);
        assertThat(points.get(2).errorsPerSecond()).isEqualTo(0.1);
    }

    @Test
    void twoSamplesSharingAnInstantProduceNoRate() {
        // Diviser par un intervalle nul rendrait l'infini, que le panel afficherait.
        List<HealthPointDto> points = HealthOverviewService.toRates(List.of(
                sample(T0, 100, 0),
                sample(T0, 200, 0)
        ));

        assertThat(points.get(1).requestsPerSecond()).isNull();
    }

    @Test
    void everythingElseIsCarriedThroughUntouched() {
        HealthSample sample = sample(T0, 100, 1);
        sample.setGateQueueSize(512);
        sample.setDiskFreeBytes(31_000_000_000L);
        sample.setStorageFreeBytes(29_000_000_000L);
        sample.setStorageTotalBytes(49_000_000_000L);
        sample.setHttpP95Millis(42.5);

        HealthPointDto point = HealthOverviewService.toRates(List.of(sample)).getFirst();

        assertThat(point.gateQueueSize()).isEqualTo(512);
        assertThat(point.diskFreeBytes()).isEqualTo(31_000_000_000L);
        assertThat(point.storageFreeBytes()).isEqualTo(29_000_000_000L);
        assertThat(point.storageTotalBytes()).isEqualTo(49_000_000_000L);
        assertThat(point.p95Millis()).isEqualTo(42.5);
        assertThat(point.at()).isEqualTo(T0);
    }

    private static HealthSample sample(Instant at, long requests, long errors) {
        HealthSample sample = new HealthSample();
        sample.setSampledAt(at);
        sample.setHttpRequestsTotal(requests);
        sample.setHttpErrorsTotal(errors);
        return sample;
    }
}
