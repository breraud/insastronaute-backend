package fr.insastronaute.webapi.cardteam.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_team_join_requests")
public class CardTeamJoinRequest {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id", nullable = false) private CardTeam team;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requester_user_id", nullable = false) private AppUser requester;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by_user_id") private AppUser decidedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CardTeamRequestStatus status = CardTeamRequestStatus.PENDING;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "responded_at") private Instant respondedAt;

    @PrePersist protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CardTeam getTeam() { return team; }
    public void setTeam(CardTeam team) { this.team = team; }
    public AppUser getRequester() { return requester; }
    public void setRequester(AppUser requester) { this.requester = requester; }
    public AppUser getDecidedBy() { return decidedBy; }
    public void setDecidedBy(AppUser decidedBy) { this.decidedBy = decidedBy; }
    public CardTeamRequestStatus getStatus() { return status; }
    public void setStatus(CardTeamRequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
