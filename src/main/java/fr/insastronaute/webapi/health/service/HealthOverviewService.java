package fr.insastronaute.webapi.health.service;

import fr.insastronaute.webapi.health.domain.HealthSample;
import fr.insastronaute.webapi.health.dto.HealthOverviewDto;
import fr.insastronaute.webapi.health.dto.HealthPointDto;
import fr.insastronaute.webapi.health.repository.HealthSampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class HealthOverviewService {

    private final HealthSampleRepository repository;
    private final HealthSampler sampler;

    public HealthOverviewService(HealthSampleRepository repository, HealthSampler sampler) {
        this.repository = repository;
        this.sampler = sampler;
    }

    @Transactional(readOnly = true)
    public HealthOverviewDto overview(Duration window) {
        List<HealthSample> samples =
                repository.findBySampledAtGreaterThanEqualOrderBySampledAtAsc(Instant.now().minus(window));
        return new HealthOverviewDto(instantPoint(), toRates(samples));
    }

    /** L'état courant, lu dans le registre sans passer par la base. */
    private HealthPointDto instantPoint() {
        return point(sampler.takeSample(), null, null);
    }

    /**
     * Transforme des cumuls en taux, par différence entre points voisins.
     *
     * <p>Une différence négative n'est pas un trafic négatif : c'est la JVM qui a
     * redémarré et remis ses compteurs à zéro. Le point est alors rendu sans taux
     * plutôt qu'avec un taux faux — un trou dans la courbe se lit, une pointe inventée
     * se croit.
     */
    static List<HealthPointDto> toRates(List<HealthSample> samples) {
        List<HealthPointDto> points = new ArrayList<>(samples.size());
        HealthSample previous = null;
        for (HealthSample sample : samples) {
            Double requests = null;
            Double errors = null;
            if (previous != null) {
                double seconds = Duration.between(previous.getSampledAt(), sample.getSampledAt()).toMillis() / 1000d;
                if (seconds > 0) {
                    requests = perSecond(previous.getHttpRequestsTotal(), sample.getHttpRequestsTotal(), seconds);
                    errors = perSecond(previous.getHttpErrorsTotal(), sample.getHttpErrorsTotal(), seconds);
                }
            }
            points.add(point(sample, requests, errors));
            previous = sample;
        }
        return points;
    }

    private static Double perSecond(long before, long after, double seconds) {
        long delta = after - before;
        return delta < 0 ? null : delta / seconds;
    }

    private static HealthPointDto point(HealthSample sample, Double requests, Double errors) {
        return new HealthPointDto(
                sample.getSampledAt(),
                requests,
                errors,
                sample.getHttpP95Millis(),
                sample.getJvmHeapUsedBytes(),
                sample.getJvmHeapMaxBytes(),
                sample.getDbPoolActive(),
                sample.getDbPoolMax(),
                sample.getDiskFreeBytes(),
                sample.getDiskTotalBytes(),
                sample.getStorageFreeBytes(),
                sample.getStorageTotalBytes(),
                sample.getGateQueueSize()
        );
    }
}
