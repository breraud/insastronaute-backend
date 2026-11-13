package fr.insastronaute.webapi.treasurehunt.domain;

import fr.insastronaute.webapi.department.domain.Department;
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
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "treasure_hunt_paths")
public class TreasureHuntPath {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, unique = true)
    private Department department;
    @Column(nullable = false, unique = true)
    private Integer position;
    @Column(nullable = false, length = 120)
    private String name;
    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber asc")
    private List<TreasureHuntCheckpoint> checkpoints = new ArrayList<>();
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TreasureHuntCheckpoint> getCheckpoints() { return checkpoints; }
    public void addCheckpoint(TreasureHuntCheckpoint checkpoint) { checkpoint.setPath(this); checkpoints.add(checkpoint); }
}
