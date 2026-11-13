package fr.insastronaute.webapi.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card implements Persistable<UUID> {

    public static final String DEFAULT_CATEGORY = "DEFAULT";
    public static final String COVER_CATEGORY = "COVER";
    public static final UUID DEFAULT_CARD_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 50)
    private String category = DEFAULT_CATEGORY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CardCategory categoryRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private CardType typeRef;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "default_card", nullable = false)
    private boolean defaultCard = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "first_acquisition_points")
    private Integer firstAcquisitionPoints;

    @Column(name = "duplicate_points")
    private Integer duplicatePoints;

    @Column(name = "duplicates_per_point")
    private Integer duplicatesPerPoint;

    @Column(name = "image_object_key", nullable = false, length = 512)
    private String imageObjectKey;

    @Column(name = "textless_image_object_key", length = 512)
    private String textlessImageObjectKey;

    @Column(name = "thumbnail_object_key", nullable = false, length = 512)
    private String thumbnailObjectKey;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "textless_image_url", length = 1024)
    private String textlessImageUrl;

    @Column(name = "thumbnail_url", nullable = false, length = 1024)
    private String thumbnailUrl;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

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

    @Override
    public UUID getId() { return id; }

    @Override
    @Transient
    public boolean isNew() { return createdAt == null; }

    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return categoryRef == null ? category : categoryRef.getName(); }
    public void setCategory(String category) { this.category = category; }
    public CardCategory getCategoryRef() { return categoryRef; }
    public void setCategoryRef(CardCategory categoryRef) {
        this.categoryRef = categoryRef;
        if (categoryRef != null) {
            this.category = categoryRef.getName();
        }
    }
    public UUID getCategoryId() { return categoryRef == null ? null : categoryRef.getId(); }
    public CardType getTypeRef() { return typeRef; }
    public void setTypeRef(CardType typeRef) { this.typeRef = typeRef; }
    public UUID getTypeId() { return typeRef == null ? null : typeRef.getId(); }
    public String getType() { return typeRef == null ? null : typeRef.getName(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDefaultCard() { return defaultCard; }
    public void setDefaultCard(boolean defaultCard) { this.defaultCard = defaultCard; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder == null ? 0 : displayOrder; }
    public Integer getFirstAcquisitionPoints() { return firstAcquisitionPoints; }
    public void setFirstAcquisitionPoints(Integer firstAcquisitionPoints) { this.firstAcquisitionPoints = firstAcquisitionPoints; }
    public Integer getDuplicatesPerPoint() { return duplicatesPerPoint; }
    public void setDuplicatesPerPoint(Integer duplicatesPerPoint) { this.duplicatesPerPoint = duplicatesPerPoint; }
    public int getEffectiveDuplicatesPerPoint() {
        if (duplicatesPerPoint != null && duplicatesPerPoint >= 1) return duplicatesPerPoint;
        if (categoryRef == null || categoryRef.getDuplicatesPerPoint() == null) return 1;
        return Math.max(1, categoryRef.getDuplicatesPerPoint());
    }
    public Integer getDuplicatePoints() { return duplicatePoints; }
    public void setDuplicatePoints(Integer duplicatePoints) { this.duplicatePoints = duplicatePoints; }
    public int getEffectiveFirstAcquisitionPoints() {
        if (categoryRef != null && categoryRef.isGame()) return 0;
        if (firstAcquisitionPoints != null) return firstAcquisitionPoints;
        return categoryRef == null ? 0 : categoryRef.getFirstAcquisitionPoints();
    }
    public int getEffectiveDuplicatePoints() {
        if (categoryRef != null && categoryRef.isGame()) return 0;
        if (duplicatePoints != null) return duplicatePoints;
        return categoryRef == null ? 0 : categoryRef.getDuplicatePoints();
    }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public String getTextlessImageObjectKey() { return textlessImageObjectKey; }
    public void setTextlessImageObjectKey(String textlessImageObjectKey) { this.textlessImageObjectKey = textlessImageObjectKey; }
    public String getThumbnailObjectKey() { return thumbnailObjectKey; }
    public void setThumbnailObjectKey(String thumbnailObjectKey) { this.thumbnailObjectKey = thumbnailObjectKey; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getTextlessImageUrl() { return textlessImageUrl; }
    public void setTextlessImageUrl(String textlessImageUrl) { this.textlessImageUrl = textlessImageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
