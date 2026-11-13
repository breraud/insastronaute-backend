package fr.insastronaute.webapi.sts.domain;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "sts_runs")
public class StsRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StsRunStatus status = StsRunStatus.Active;

    @Column(name = "data_version", nullable = false)
    private String dataVersion;

    @Column(name = "selected_character", nullable = false)
    private String selectedCharacter;

    @Column(nullable = false)
    private Integer act = 0;

    @Column(name = "current_floor", nullable = false)
    private Integer currentFloor = 0;

    @Column(name = "current_node_id", nullable = false)
    private Integer currentNodeId = 0;

    @Column(name = "entered_node_id")
    private Integer enteredNodeId;

    @Column(name = "retirement_score")
    private Long retirementScore;

    @Column(name = "retirement_tokens")
    private Long retirementTokens;

    @Column(name = "pvp_unlock_claimed", nullable = false)
    private boolean pvpUnlockClaimed = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "player_state", nullable = false, columnDefinition = "jsonb")
    private JsonNode playerState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "run_inventory", nullable = false, columnDefinition = "jsonb")
    private JsonNode runInventory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "map_state", nullable = false, columnDefinition = "jsonb")
    private JsonNode mapState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_rewards", nullable = false, columnDefinition = "jsonb")
    private JsonNode pendingRewards;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_encounter", columnDefinition = "jsonb")
    private JsonNode activeEncounter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_combat", columnDefinition = "jsonb")
    private JsonNode activeCombat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_event", columnDefinition = "jsonb")
    private JsonNode activeEvent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public StsRunStatus getStatus() { return status; }
    public void setStatus(StsRunStatus status) { this.status = status; }
    public String getDataVersion() { return dataVersion; }
    public void setDataVersion(String dataVersion) { this.dataVersion = dataVersion; }
    public String getSelectedCharacter() { return selectedCharacter; }
    public void setSelectedCharacter(String selectedCharacter) { this.selectedCharacter = selectedCharacter; }
    public Integer getAct() { return act; }
    public void setAct(Integer act) { this.act = act; }
    public Integer getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(Integer currentFloor) { this.currentFloor = currentFloor; }
    public Integer getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(Integer currentNodeId) { this.currentNodeId = currentNodeId; }
    public Integer getEnteredNodeId() { return enteredNodeId; }
    public void setEnteredNodeId(Integer enteredNodeId) { this.enteredNodeId = enteredNodeId; }
    public Long getRetirementScore() { return retirementScore; }
    public void setRetirementScore(Long retirementScore) { this.retirementScore = retirementScore; }
    public Long getRetirementTokens() { return retirementTokens; }
    public void setRetirementTokens(Long retirementTokens) { this.retirementTokens = retirementTokens; }
    public boolean isPvpUnlockClaimed() { return pvpUnlockClaimed; }
    public void setPvpUnlockClaimed(boolean pvpUnlockClaimed) { this.pvpUnlockClaimed = pvpUnlockClaimed; }
    public JsonNode getPlayerState() { return playerState; }
    public void setPlayerState(JsonNode playerState) { this.playerState = playerState; }
    public JsonNode getRunInventory() { return runInventory; }
    public void setRunInventory(JsonNode runInventory) { this.runInventory = runInventory; }
    public JsonNode getMapState() { return mapState; }
    public void setMapState(JsonNode mapState) { this.mapState = mapState; }
    public JsonNode getPendingRewards() { return pendingRewards; }
    public void setPendingRewards(JsonNode pendingRewards) { this.pendingRewards = pendingRewards; }
    public JsonNode getActiveEncounter() { return activeEncounter; }
    public void setActiveEncounter(JsonNode activeEncounter) { this.activeEncounter = activeEncounter; }
    public JsonNode getActiveCombat() { return activeCombat; }
    public void setActiveCombat(JsonNode activeCombat) { this.activeCombat = activeCombat; }
    public JsonNode getActiveEvent() { return activeEvent; }
    public void setActiveEvent(JsonNode activeEvent) { this.activeEvent = activeEvent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
