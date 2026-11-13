package fr.insastronaute.webapi.health.service;

import fr.insastronaute.webapi.health.domain.HealthSample;
import fr.insastronaute.webapi.health.repository.HealthSampleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HealthSamplerTest {

    @TempDir
    Path storage;

    @Test
    void theStorageDiskIsMeasuredWhenAPathIsConfigured() {
        HealthSample sample = sampler(storage.toString()).takeSample();

        assertThat(sample.getStorageTotalBytes()).isPositive();
        assertThat(sample.getStorageFreeBytes()).isPositive();
    }

    @Test
    void noPathMeansNoStorageMeasure() {
        HealthSample sample = sampler("").takeSample();

        assertThat(sample.getStorageTotalBytes()).isNull();
        assertThat(sample.getStorageFreeBytes()).isNull();
    }

    @Test
    void aPathThatIsNotMountedIsNotReadAsAnEmptyDisk() {
        HealthSample sample = sampler(storage.resolve("absent").toString()).takeSample();

        assertThat(sample.getStorageTotalBytes()).isNull();
        assertThat(sample.getStorageFreeBytes()).isNull();
    }

    @Test
    void theSystemDiskIsStillMeasured() {
        HealthSample sample = sampler("").takeSample();

        assertThat(sample.getDiskTotalBytes()).isPositive();
    }

    private static HealthSampler sampler(String storagePath) {
        return new HealthSampler(
                new SimpleMeterRegistry(),
                mock(HealthSampleRepository.class),
                mock(StringRedisTemplate.class),
                Duration.ofDays(30),
                "/",
                storagePath
        );
    }
}
