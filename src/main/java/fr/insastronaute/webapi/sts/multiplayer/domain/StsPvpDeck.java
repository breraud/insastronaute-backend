package fr.insastronaute.webapi.sts.multiplayer.domain;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sts_pvp_decks")
public class StsPvpDeck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "selected_character", nullable = false)
    private String selectedCharacter;

    @Column(name = "is_active", nullable = false)
    private Boolean active = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_ids", nullable = false, columnDefinition = "jsonb")
    private JsonNode cardIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSelectedCharacter() { return selectedCharacter; }
    public void setSelectedCharacter(String selectedCharacter) { this.selectedCharacter = selectedCharacter; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public JsonNode getCardIds() { return cardIds; }
    public void setCardIds(JsonNode cardIds) { this.cardIds = cardIds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
