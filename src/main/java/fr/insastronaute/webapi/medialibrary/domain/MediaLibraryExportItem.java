package fr.insastronaute.webapi.medialibrary.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Un media fige dans un export, avec la partie qui le portera. */
@Entity
@Table(name = "media_library_export_items")
public class MediaLibraryExportItem {

    @EmbeddedId
    private MediaExportItemKey id;

    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "media_index", nullable = false)
    private int mediaIndex;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_by", nullable = false)
    private AppUser selectedBy;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    protected MediaLibraryExportItem() {
    }

    public MediaLibraryExportItem(MediaExportItemKey id, int partNumber, UUID submissionId, int mediaIndex,
                                  long sizeBytes, AppUser selectedBy, Instant selectedAt) {
        this.id = id;
        this.partNumber = partNumber;
        this.submissionId = submissionId;
        this.mediaIndex = mediaIndex;
        this.sizeBytes = sizeBytes;
        this.selectedBy = selectedBy;
        this.selectedAt = selectedAt;
    }

    public MediaExportItemKey getId() { return id; }
    public int getPartNumber() { return partNumber; }
    public UUID getSubmissionId() { return submissionId; }
    public int getMediaIndex() { return mediaIndex; }
    public long getSizeBytes() { return sizeBytes; }
    public AppUser getSelectedBy() { return selectedBy; }
    public Instant getSelectedAt() { return selectedAt; }
}
