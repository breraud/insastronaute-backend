package fr.insastronaute.webapi.health.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un relevé de l'état du service, pris toutes les trente secondes.
 *
 * <p>Les compteurs HTTP sont conservés <em>cumulatifs</em>, comme Micrometer les tient.
 * En faire des taux au moment de l'écriture obligerait l'échantillonneur à se souvenir
 * du relevé précédent, ce qu'un redémarrage lui ferait oublier. La différence se calcule
 * donc à la lecture, entre deux lignes voisines.
 */
@Entity
@Table(name = "health_samples")
public class HealthSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "http_requests_total", nullable = false)
    private long httpRequestsTotal;

    @Column(name = "http_errors_total", nullable = false)
    private long httpErrorsTotal;

    @Column(name = "http_p95_millis")
    private Double httpP95Millis;

    @Column(name = "jvm_heap_used_bytes")
    private Long jvmHeapUsedBytes;

    @Column(name = "jvm_heap_max_bytes")
    private Long jvmHeapMaxBytes;

    @Column(name = "db_pool_active")
    private Integer dbPoolActive;

    @Column(name = "db_pool_max")
    private Integer dbPoolMax;

    @Column(name = "disk_free_bytes")
    private Long diskFreeBytes;

    @Column(name = "disk_total_bytes")
    private Long diskTotalBytes;

    @Column(name = "storage_free_bytes")
    private Long storageFreeBytes;

    @Column(name = "storage_total_bytes")
    private Long storageTotalBytes;

    @Column(name = "gate_queue_size")
    private Integer gateQueueSize;

    public UUID getId() { return id; }

    public Instant getSampledAt() { return sampledAt; }
    public void setSampledAt(Instant sampledAt) { this.sampledAt = sampledAt; }

    public long getHttpRequestsTotal() { return httpRequestsTotal; }
    public void setHttpRequestsTotal(long httpRequestsTotal) { this.httpRequestsTotal = httpRequestsTotal; }

    public long getHttpErrorsTotal() { return httpErrorsTotal; }
    public void setHttpErrorsTotal(long httpErrorsTotal) { this.httpErrorsTotal = httpErrorsTotal; }

    public Double getHttpP95Millis() { return httpP95Millis; }
    public void setHttpP95Millis(Double httpP95Millis) { this.httpP95Millis = httpP95Millis; }

    public Long getJvmHeapUsedBytes() { return jvmHeapUsedBytes; }
    public void setJvmHeapUsedBytes(Long jvmHeapUsedBytes) { this.jvmHeapUsedBytes = jvmHeapUsedBytes; }

    public Long getJvmHeapMaxBytes() { return jvmHeapMaxBytes; }
    public void setJvmHeapMaxBytes(Long jvmHeapMaxBytes) { this.jvmHeapMaxBytes = jvmHeapMaxBytes; }

    public Integer getDbPoolActive() { return dbPoolActive; }
    public void setDbPoolActive(Integer dbPoolActive) { this.dbPoolActive = dbPoolActive; }

    public Integer getDbPoolMax() { return dbPoolMax; }
    public void setDbPoolMax(Integer dbPoolMax) { this.dbPoolMax = dbPoolMax; }

    public Long getDiskFreeBytes() { return diskFreeBytes; }
    public void setDiskFreeBytes(Long diskFreeBytes) { this.diskFreeBytes = diskFreeBytes; }

    public Long getDiskTotalBytes() { return diskTotalBytes; }
    public void setDiskTotalBytes(Long diskTotalBytes) { this.diskTotalBytes = diskTotalBytes; }

    public Long getStorageFreeBytes() { return storageFreeBytes; }
    public void setStorageFreeBytes(Long storageFreeBytes) { this.storageFreeBytes = storageFreeBytes; }

    public Long getStorageTotalBytes() { return storageTotalBytes; }
    public void setStorageTotalBytes(Long storageTotalBytes) { this.storageTotalBytes = storageTotalBytes; }

    public Integer getGateQueueSize() { return gateQueueSize; }
    public void setGateQueueSize(Integer gateQueueSize) { this.gateQueueSize = gateQueueSize; }
}
