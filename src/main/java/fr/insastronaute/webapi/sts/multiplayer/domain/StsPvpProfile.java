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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sts_pvp_profiles")
public class StsPvpProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "selected_character", nullable = false)
    private String selectedCharacter = "EP";

    @Column(name = "elo", nullable = false)
    private Integer elo = 1000;

    @Column(name = "wins_ranked", nullable = false)
    private Integer winsRanked = 0;

    @Column(name = "losses_ranked", nullable = false)
    private Integer lossesRanked = 0;

    @Column(name = "wins_friendly", nullable = false)
    private Integer winsFriendly = 0;

    @Column(name = "losses_friendly", nullable = false)
    private Integer lossesFriendly = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "character_exp", nullable = false, columnDefinition = "jsonb")
    private JsonNode characterExp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "character_stats", nullable = false, columnDefinition = "jsonb")
    private JsonNode characterStats;

    /** Run-end-unlocked STS card keys (collection card id, or the card's own name), as a JSON array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unlocked_cards", nullable = false, columnDefinition = "jsonb")
    private JsonNode unlockedCards;

    @Column(name = "preferred_friendly", nullable = false)
    private Boolean preferredFriendly = false;

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
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public String getSelectedCharacter() { return selectedCharacter; }
    public void setSelectedCharacter(String selectedCharacter) { this.selectedCharacter = selectedCharacter; }
    public Integer getElo() { return elo; }
    public void setElo(Integer elo) { this.elo = elo; }
    public Integer getWinsRanked() { return winsRanked; }
    public void setWinsRanked(Integer winsRanked) { this.winsRanked = winsRanked; }
    public Integer getLossesRanked() { return lossesRanked; }
    public void setLossesRanked(Integer lossesRanked) { this.lossesRanked = lossesRanked; }
    public Integer getWinsFriendly() { return winsFriendly; }
    public void setWinsFriendly(Integer winsFriendly) { this.winsFriendly = winsFriendly; }
    public Integer getLossesFriendly() { return lossesFriendly; }
    public void setLossesFriendly(Integer lossesFriendly) { this.lossesFriendly = lossesFriendly; }
    public JsonNode getCharacterExp() { return characterExp; }
    public void setCharacterExp(JsonNode characterExp) { this.characterExp = characterExp; }
    public JsonNode getCharacterStats() { return characterStats; }
    public void setCharacterStats(JsonNode characterStats) { this.characterStats = characterStats; }
    public JsonNode getUnlockedCards() { return unlockedCards; }
    public void setUnlockedCards(JsonNode unlockedCards) { this.unlockedCards = unlockedCards; }
    public Boolean getPreferredFriendly() { return preferredFriendly; }
    public void setPreferredFriendly(Boolean preferredFriendly) { this.preferredFriendly = preferredFriendly; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
