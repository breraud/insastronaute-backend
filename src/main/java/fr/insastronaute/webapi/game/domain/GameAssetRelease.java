package fr.insastronaute.webapi.game.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "game_asset_releases")
public class GameAssetRelease {

    public static final Long ACTIVE_RELEASE_ID = 1L;

    @Id
    private Long id = ACTIVE_RELEASE_ID;

    @Column(nullable = false, length = 80)
    private String version;

    @Column(name = "object_prefix", nullable = false, length = 512)
    private String objectPrefix;

    @Column(name = "base_url", nullable = false, length = 1024)
    private String baseUrl;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.publishedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getObjectPrefix() { return objectPrefix; }
    public void setObjectPrefix(String objectPrefix) { this.objectPrefix = objectPrefix; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
