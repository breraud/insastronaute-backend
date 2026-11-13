package fr.insastronaute.webapi.tournament.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.event.domain.Event;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * What a player was actually granted for one tournament. Re-settling compares
 * against this ledger and applies only the difference, so validating twice
 * never stacks points and un-checking somebody gives them back.
 */
@Entity
@Table(
        name = "tournament_point_awards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tournament_point_awards_event_user",
                columnNames = {"event_id", "user_id"}
        )
)
public class TournamentPointAward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "participation_points", nullable = false)
    private Integer participationPoints = 0;

    @Column(name = "victory_points", nullable = false)
    private Integer victoryPoints = 0;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Column(name = "tier_label", length = 80)
    private String tierLabel;

    @Column(name = "team_name", length = 120)
    private String teamName;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @PrePersist
    @PreUpdate
    protected void onWrite() {
        this.awardedAt = TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Integer getParticipationPoints() { return participationPoints; }
    public void setParticipationPoints(Integer participationPoints) {
        this.participationPoints = participationPoints == null ? 0 : participationPoints;
    }
    public Integer getVictoryPoints() { return victoryPoints; }
    public void setVictoryPoints(Integer victoryPoints) {
        this.victoryPoints = victoryPoints == null ? 0 : victoryPoints;
    }
    public Integer getTotalPoints() { return totalPoints; }
    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints == null ? 0 : totalPoints;
    }
    public String getTierLabel() { return tierLabel; }
    public void setTierLabel(String tierLabel) { this.tierLabel = tierLabel; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Instant getAwardedAt() { return awardedAt; }
}
