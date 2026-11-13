package fr.insastronaute.webapi.leaderboard.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "leaderboard_entries")
public class LeaderboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private LeaderboardSnapshot snapshot;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String department;

    @Column(nullable = false)
    private Long score;
    // Le detail du score d'un departement, fige a la publication : il permet de
    // montrer d'ou vient le score affiche sans le recalculer. Nul pour un joueur.
    @Column(name = "raw_total")
    private Long rawTotal;
    @Column(name = "member_count")
    private Integer memberCount;
    @Column(name = "theoretical_headcount")
    private Integer theoreticalHeadcount;
    @Column(name = "coefficient")
    private Double coefficient;
    @Column(name = "promo_points")
    private Long promoPoints;
    @Column(name = "formula", length = 40)
    private String formula;
    @Column(name = "reference_headcount")
    private Integer referenceHeadcount;

    public UUID getId() { return id; }
    public LeaderboardSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(LeaderboardSnapshot snapshot) { this.snapshot = snapshot; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public Long getRawTotal() { return rawTotal; }
    public void setRawTotal(Long rawTotal) { this.rawTotal = rawTotal; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getTheoreticalHeadcount() { return theoreticalHeadcount; }
    public void setTheoreticalHeadcount(Integer theoreticalHeadcount) { this.theoreticalHeadcount = theoreticalHeadcount; }
    public Double getCoefficient() { return coefficient; }
    public void setCoefficient(Double coefficient) { this.coefficient = coefficient; }
    public Long getPromoPoints() { return promoPoints; }
    public void setPromoPoints(Long promoPoints) { this.promoPoints = promoPoints; }
    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }
    public Integer getReferenceHeadcount() { return referenceHeadcount; }
    public void setReferenceHeadcount(Integer referenceHeadcount) { this.referenceHeadcount = referenceHeadcount; }
}
