package fr.insastronaute.webapi.card.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Singleton knobs for the collection point economy, editable from the panel. */
@Entity
@Table(name = "card_collection_settings")
public class CardCollectionSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "physical_first_acquisition_multiplier", nullable = false)
    private Integer physicalFirstAcquisitionMultiplier = 2;

    @Column(name = "physical_duplicates_enabled", nullable = false)
    private Boolean physicalDuplicatesEnabled = false;

    @Column(name = "tokens_per_point", nullable = false)
    private Integer tokensPerPoint = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onWrite() {
        this.updatedAt = TimeTravelContext.instantNow();
    }

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public Integer getPhysicalFirstAcquisitionMultiplier() { return physicalFirstAcquisitionMultiplier; }
    public void setPhysicalFirstAcquisitionMultiplier(Integer value) {
        this.physicalFirstAcquisitionMultiplier = value == null || value < 0 ? 0 : value;
    }
    public Boolean getPhysicalDuplicatesEnabled() { return physicalDuplicatesEnabled; }
    public void setPhysicalDuplicatesEnabled(Boolean value) {
        this.physicalDuplicatesEnabled = value != null && value;
    }
    public Integer getTokensPerPoint() { return tokensPerPoint; }
    public void setTokensPerPoint(Integer value) {
        this.tokensPerPoint = value == null || value < 0 ? 0 : value;
    }
    public Instant getUpdatedAt() { return updatedAt; }
}
