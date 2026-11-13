package fr.insastronaute.webapi.medialibrary.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Un instantane de la selection, decoupe en parties a telecharger. */
@Entity
@Table(name = "media_library_exports")
public class MediaLibraryExport {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "part_count", nullable = false)
    private int partCount;

    protected MediaLibraryExport() {
    }

    public MediaLibraryExport(UUID id, AppUser createdBy, Instant createdAt, int itemCount, long totalBytes, int partCount) {
        this.id = id;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
        this.totalBytes = totalBytes;
        this.partCount = partCount;
    }

    public UUID getId() { return id; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public int getItemCount() { return itemCount; }
    public long getTotalBytes() { return totalBytes; }
    public int getPartCount() { return partCount; }
}
