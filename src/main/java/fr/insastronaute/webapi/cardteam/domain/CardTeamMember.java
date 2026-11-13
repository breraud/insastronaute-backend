package fr.insastronaute.webapi.cardteam.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_team_members", uniqueConstraints = {
        @UniqueConstraint(name = "ux_card_team_members_user", columnNames = "user_id"),
        @UniqueConstraint(name = "ux_card_team_members_team_user", columnNames = {"team_id", "user_id"})
})
public class CardTeamMember {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private CardTeam team;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (joinedAt == null) joinedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CardTeam getTeam() { return team; }
    public void setTeam(CardTeam team) { this.team = team; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
