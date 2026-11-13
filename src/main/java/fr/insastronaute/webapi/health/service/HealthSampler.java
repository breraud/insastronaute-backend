package fr.insastronaute.webapi.health.service;

import fr.insastronaute.webapi.health.domain.HealthSample;
import fr.insastronaute.webapi.health.repository.HealthSampleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Écrit toutes les trente secondes ce que le service est en train de vivre.
 *
 * <p>Micrometer tient ses compteurs en mémoire : ils repartent de zéro à chaque
 * déploiement. Les relever dans Postgres leur donne une mémoire qui survit au
 * redémarrage — c'est le seul gain qu'on cherchait d'une base de séries temporelles,
 * et le seul qu'on puisse s'offrir sur une machine sans mémoire vive à donner.
 *
 * <p>Rien ici ne doit faire échouer quoi que ce soit : un relevé manquant est une
 * courbe trouée, une exception qui remonte est un incident. Chaque lecture est donc
 * défensive, et l'échantillonnage entier est enveloppé.
 */
@Service
public class HealthSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthSampler.class);
    private static final String GATE_QUEUE_KEY = "auth:gate:queue";

    private final MeterRegistry meterRegistry;
    private final HealthSampleRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final Duration retention;
    private final File diskRoot;
    private final File storageRoot;

    public HealthSampler(
            MeterRegistry meterRegistry,
            HealthSampleRepository repository,
            StringRedisTemplate redisTemplate,
            @Value("${app.health.retention:P30D}") Duration retention,
            @Value("${app.health.disk-path:/}") String diskPath,
            @Value("${app.health.storage-path:}") String storagePath
    ) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.retention = retention;
        this.diskRoot = new File(diskPath);
        this.storageRoot = storagePath.isBlank() ? null : new File(storagePath);
    }

    @Scheduled(fixedDelayString = "${app.health.sample-interval-ms:30000}")
    @Transactional
    public void sample() {
        try {
            repository.save(takeSample());
        } catch (RuntimeException exception) {
            LOGGER.warn("Relevé de santé ignoré : {}", exception.getMessage());
        }
    }

    /**
     * La purge tourne une fois par jour, pas à chaque relevé : supprimer sur une table
     * où l'on insère deux fois par minute coûte plus que ce qu'elle rend.
     */
    @Scheduled(cron = "${app.health.purge-cron:0 15 4 * * *}")
    @Transactional
    public void purgeOldSamples() {
        try {
            int deleted = repository.deleteOlderThan(Instant.now().minus(retention));
            if (deleted > 0) {
                LOGGER.info("Relevés de santé purgés : {}", deleted);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Purge des relevés de santé ignorée : {}", exception.getMessage());
        }
    }

    HealthSample takeSample() {
        HealthSample sample = new HealthSample();
        sample.setSampledAt(Instant.now());

        Timer requests = Search.in(meterRegistry).name("http.server.requests").timer();
        if (requests != null) {
            sample.setHttpRequestsTotal(requests.count());
            sample.setHttpP95Millis(percentile(requests, 0.95));
        }
        // Les 5xx seulement : un 404 est une adresse qui n'existe pas, pas une panne.
        sample.setHttpErrorsTotal(Search.in(meterRegistry)
                .name("http.server.requests")
                .tag("outcome", "SERVER_ERROR")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum());

        sample.setJvmHeapUsedBytes(gauge("jvm.memory.used", "area", "heap"));
        sample.setJvmHeapMaxBytes(gauge("jvm.memory.max", "area", "heap"));

        Long active = gauge("hikaricp.connections.active", null, null);
        Long max = gauge("hikaricp.connections.max", null, null);
        sample.setDbPoolActive(active == null ? null : active.intValue());
        sample.setDbPoolMax(max == null ? null : max.intValue());

        // Le disque est lu directement : la métrique disk.free d'Actuator ne couvre que
        // le chemin qu'on lui a désigné, et c'est la racine qui nous intéresse.
        long total = diskRoot.getTotalSpace();
        if (total > 0) {
            sample.setDiskTotalBytes(total);
            sample.setDiskFreeBytes(diskRoot.getUsableSpace());
        }
        // Le disque de SeaweedFS, monte a part. Un chemin absent rend un total nul :
        // mieux vaut une courbe trouee qu'un disque « vide » qui se lirait comme sain.
        if (storageRoot != null) {
            long storageTotal = storageRoot.getTotalSpace();
            if (storageTotal > 0) {
                sample.setStorageTotalBytes(storageTotal);
                sample.setStorageFreeBytes(storageRoot.getUsableSpace());
            }
        }

        sample.setGateQueueSize(gateQueueSize());
        return sample;
    }

    /**
     * Le percentile demandé, ou rien s'il n'est pas publié.
     *
     * <p>Il n'existe que parce que la configuration le réclame nommément
     * ({@code management.metrics.distribution.percentiles}). Sans elle, le minuteur ne
     * rend que compte, total et maximum — et cette méthode rend null plutôt que zéro,
     * qui se lirait comme « instantané ».
     */
    private Double percentile(Timer timer, double percentile) {
        for (io.micrometer.core.instrument.distribution.ValueAtPercentile value
                : timer.takeSnapshot().percentileValues()) {
            if (Math.abs(value.percentile() - percentile) < 0.0001) {
                return value.value(TimeUnit.MILLISECONDS);
            }
        }
        return null;
    }

    private Long gauge(String name, String tagKey, String tagValue) {
        try {
            Search search = Search.in(meterRegistry).name(name);
            if (tagKey != null) {
                search = search.tag(tagKey, tagValue);
            }
            double sum = search.gauges().stream().mapToDouble(io.micrometer.core.instrument.Gauge::value).sum();
            return Double.isFinite(sum) ? (long) sum : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * La file du gate a coûté une nuit à comprendre faute d'une courbe : un compteur
     * qui monte sans charge derrière se lit d'un coup d'œil, et jamais dans un HAR.
     */
    private Integer gateQueueSize() {
        try {
            Long size = redisTemplate.opsForZSet().size(GATE_QUEUE_KEY);
            return size == null ? null : size.intValue();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
