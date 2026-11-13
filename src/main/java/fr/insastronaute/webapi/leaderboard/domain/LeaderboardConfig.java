package fr.insastronaute.webapi.leaderboard.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "leaderboard_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_leaderboard_configs_scope_metric", columnNames = {"scope", "metric"})
)
public class LeaderboardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaderboardScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LeaderboardMetric metric;

    @Column(name = "refresh_delay_minutes", nullable = false)
    private Integer refreshDelayMinutes = 60;

    @Column(name = "auto_refresh_enabled", nullable = false)
    private Boolean autoRefreshEnabled = false;

    @Column(name = "total_weight_percent", nullable = false)
    private Integer totalWeightPercent = 20;

    /**
     * Ce par quoi les points de la metrique sont multiplies avant leur poids, dans le total
     * seulement. Les cartes se comptent en milliers quand les defis se comptent en centaines
     * de milliers : sans lui, le poids d'une carte ne pese presque rien.
     */
    @Column(name = "total_scale_factor", nullable = false)
    private Integer totalScaleFactor = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DepartmentScoringFormula formula = DepartmentScoringFormula.RAW_TOTAL;

    @Column(name = "last_released_at")
    private Instant lastReleasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LeaderboardScope getScope() { return scope; }
    public void setScope(LeaderboardScope scope) { this.scope = scope; }
    public LeaderboardMetric getMetric() { return metric; }
    public void setMetric(LeaderboardMetric metric) { this.metric = metric; }
    public Integer getRefreshDelayMinutes() { return refreshDelayMinutes; }
    public void setRefreshDelayMinutes(Integer refreshDelayMinutes) { this.refreshDelayMinutes = refreshDelayMinutes; }
    public Boolean getAutoRefreshEnabled() { return autoRefreshEnabled; }
    public void setAutoRefreshEnabled(Boolean autoRefreshEnabled) { this.autoRefreshEnabled = autoRefreshEnabled; }
    public Integer getTotalWeightPercent() { return totalWeightPercent; }
    public void setTotalWeightPercent(Integer totalWeightPercent) { this.totalWeightPercent = totalWeightPercent; }
    public Integer getTotalScaleFactor() { return totalScaleFactor; }
    public void setTotalScaleFactor(Integer totalScaleFactor) { this.totalScaleFactor = totalScaleFactor; }
    public DepartmentScoringFormula getFormula() { return formula; }
    public void setFormula(DepartmentScoringFormula formula) { this.formula = formula; }
    public Instant getLastReleasedAt() { return lastReleasedAt; }
    public void setLastReleasedAt(Instant lastReleasedAt) { this.lastReleasedAt = lastReleasedAt; }
}
