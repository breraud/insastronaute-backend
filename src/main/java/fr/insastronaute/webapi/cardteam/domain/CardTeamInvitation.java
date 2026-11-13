package fr.insastronaute.webapi.cardteam.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_team_invitations")
public class CardTeamInvitation {
    @Id private UUID id;
    /**
     * L'equipe rejointe, ou {@code null} si elle n'existe pas encore.
     *
     * <p>Celui qui invite peut n'avoir aucune equipe : une equipe naissant a deux, il n'y
     * a rien vers quoi pointer tant que l'autre n'a pas accepte. {@link #proposedTeamName}
     * porte alors le nom qu'elle aura. La contrainte en base impose exactement l'un des
     * deux -- une invitation sans equipe ni nom n'aboutirait a rien.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id") private CardTeam team;

    @Column(name = "proposed_team_name", length = 60) private String proposedTeamName;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inviter_user_id", nullable = false) private AppUser inviter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invited_user_id", nullable = false) private AppUser invited;
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
    public String getProposedTeamName() { return proposedTeamName; }
    public void setProposedTeamName(String proposedTeamName) { this.proposedTeamName = proposedTeamName; }
    public AppUser getInviter() { return inviter; }
    public void setInviter(AppUser inviter) { this.inviter = inviter; }
    public AppUser getInvited() { return invited; }
    public void setInvited(AppUser invited) { this.invited = invited; }
    public CardTeamRequestStatus getStatus() { return status; }
    public void setStatus(CardTeamRequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
