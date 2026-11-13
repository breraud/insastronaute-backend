package fr.insastronaute.webapi.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_categories")
public class CardCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "image_object_key", length = 512)
    private String imageObjectKey;

    @Column(name = "thumbnail_object_key", length = 512)
    private String thumbnailObjectKey;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(nullable = false)
    private boolean game = false;

    @Column(nullable = false)
    private boolean extension = false;

    @Column(name = "first_acquisition_points", nullable = false)
    private Integer firstAcquisitionPoints = 0;

    @Column(name = "duplicate_points", nullable = false)
    private Integer duplicatePoints = 0;

    /** A common card is only worth its duplicate points every N extra copies. */
    @Column(name = "duplicates_per_point", nullable = false)
    private Integer duplicatesPerPoint = 1;

    /** La mise en scène jouée à l'ouverture pour les cartes de cette catégorie. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reveal_animation", nullable = false, length = 24)
    private CardRevealAnimation revealAnimation = CardRevealAnimation.AUTO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder == null ? 0 : displayOrder; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public String getThumbnailObjectKey() { return thumbnailObjectKey; }
    public void setThumbnailObjectKey(String thumbnailObjectKey) { this.thumbnailObjectKey = thumbnailObjectKey; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public boolean isGame() { return game; }
    public void setGame(boolean game) { this.game = game; }
    public boolean isExtension() { return extension; }
    public void setExtension(boolean extension) { this.extension = extension; }
    public Integer getFirstAcquisitionPoints() { return firstAcquisitionPoints; }
    public void setFirstAcquisitionPoints(Integer firstAcquisitionPoints) {
        this.firstAcquisitionPoints = firstAcquisitionPoints == null ? 0 : firstAcquisitionPoints;
    }
    public CardRevealAnimation getRevealAnimation() { return revealAnimation; }
    public void setRevealAnimation(CardRevealAnimation revealAnimation) {
        this.revealAnimation = revealAnimation == null ? CardRevealAnimation.AUTO : revealAnimation;
    }
    public Integer getDuplicatesPerPoint() { return duplicatesPerPoint; }
    public void setDuplicatesPerPoint(Integer duplicatesPerPoint) {
        this.duplicatesPerPoint = duplicatesPerPoint == null || duplicatesPerPoint < 1 ? 1 : duplicatesPerPoint;
    }
    public Integer getDuplicatePoints() { return duplicatePoints; }
    public void setDuplicatePoints(Integer duplicatePoints) {
        this.duplicatePoints = duplicatePoints == null ? 0 : duplicatePoints;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
