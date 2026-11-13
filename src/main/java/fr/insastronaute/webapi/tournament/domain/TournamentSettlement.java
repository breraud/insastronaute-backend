package fr.insastronaute.webapi.tournament.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.event.domain.Event;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The organiser's scoring setup for one tournament: how many points simply
 * showing up is worth, plus the victory tiers layered on top.
 */
@Entity
@Table(
        name = "tournament_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_tournament_settlements_event", columnNames = "event_id")
)
public class TournamentSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "participation_points", nullable = false)
    private Integer participationPoints = 0;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by_user_id")
    private UUID settledByUserId;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<TournamentVictoryTier> tiers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = TimeTravelContext.instantNow();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Integer getParticipationPoints() { return participationPoints; }
    public void setParticipationPoints(Integer participationPoints) {
        this.participationPoints = participationPoints == null ? 0 : participationPoints;
    }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    public UUID getSettledByUserId() { return settledByUserId; }
    public void setSettledByUserId(UUID settledByUserId) { this.settledByUserId = settledByUserId; }
    public List<TournamentVictoryTier> getTiers() { return tiers; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
