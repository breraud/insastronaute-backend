package fr.insastronaute.webapi.medialibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Un media : une soumission, et la position du fichier dans sa preuve. */
@Embeddable
public class MediaSelectionKey implements Serializable {

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "media_index", nullable = false)
    private int mediaIndex;

    protected MediaSelectionKey() {
    }

    public MediaSelectionKey(UUID submissionId, int mediaIndex) {
        this.submissionId = submissionId;
        this.mediaIndex = mediaIndex;
    }

    public UUID getSubmissionId() { return submissionId; }
    public int getMediaIndex() { return mediaIndex; }

    @Override
    public boolean equals(Object other) {
        return other instanceof MediaSelectionKey key
                && mediaIndex == key.mediaIndex
                && Objects.equals(submissionId, key.submissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(submissionId, mediaIndex);
    }
}
