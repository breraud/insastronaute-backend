package fr.insastronaute.webapi.tournament.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One podium step. It holds several members at once so a winning team shares
 * the same bonus, and several tiers can coexist for 1st, 2nd, 3rd and so on.
 */
@Entity
@Table(name = "tournament_victory_tiers")
public class TournamentVictoryTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private TournamentSettlement settlement;

    @Column(nullable = false)
    private Integer position = 0;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private Integer points = 0;

    @Column(name = "team_name", length = 120)
    private String teamName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tournament_tier_members",
            joinColumns = @JoinColumn(name = "tier_id")
    )
    @Column(name = "user_id", nullable = false)
    private Set<UUID> memberUserIds = new LinkedHashSet<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TournamentSettlement getSettlement() { return settlement; }
    public void setSettlement(TournamentSettlement settlement) { this.settlement = settlement; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position == null ? 0 : position; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points == null ? 0 : points; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Set<UUID> getMemberUserIds() { return memberUserIds; }
    public void setMemberUserIds(Set<UUID> memberUserIds) {
        this.memberUserIds = memberUserIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(memberUserIds);
    }
}
