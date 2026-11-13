package fr.insastronaute.webapi.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Qui a refermé quelle annonce. Suit le joueur, pas l'appareil. */
@Entity
@Table(name = "announcement_dismissals")
public class AnnouncementDismissal {

    @EmbeddedId
    private Key key;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;

    protected AnnouncementDismissal() {
    }

    public AnnouncementDismissal(UUID announcementId, UUID userId, Instant dismissedAt) {
        this.key = new Key(announcementId, userId);
        this.dismissedAt = dismissedAt;
    }

    public Key getKey() { return key; }
    public Instant getDismissedAt() { return dismissedAt; }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "announcement_id", nullable = false)
        private UUID announcementId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID announcementId, UUID userId) {
            this.announcementId = announcementId;
            this.userId = userId;
        }

        public UUID getAnnouncementId() { return announcementId; }
        public UUID getUserId() { return userId; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(announcementId, key.announcementId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(announcementId, userId);
        }
    }
}
