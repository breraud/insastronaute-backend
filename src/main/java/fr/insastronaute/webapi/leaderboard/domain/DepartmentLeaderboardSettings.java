package fr.insastronaute.webapi.leaderboard.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.department.domain.Department;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "department_leaderboard_settings")
public class DepartmentLeaderboardSettings {

    @Id
    @Column(name = "department_id")
    private UUID departmentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "theoretical_headcount", nullable = false)
    private Integer theoreticalHeadcount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = TimeTravelContext.instantNow();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeTravelContext.instantNow();
    }

    public UUID getDepartmentId() { return departmentId; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Integer getTheoreticalHeadcount() { return theoreticalHeadcount; }

    public void setTheoreticalHeadcount(Integer theoreticalHeadcount) {
        if (theoreticalHeadcount == null || theoreticalHeadcount < 1) {
            throw new IllegalArgumentException("Theoretical headcount must be positive.");
        }
        this.theoreticalHeadcount = theoreticalHeadcount;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
