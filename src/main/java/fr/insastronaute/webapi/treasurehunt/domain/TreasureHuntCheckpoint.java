package fr.insastronaute.webapi.treasurehunt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "treasure_hunt_checkpoints")
public class TreasureHuntCheckpoint {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "path_id", nullable = false)
    private TreasureHuntPath path;
    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;
    @Column(name = "code_hash", nullable = false, unique = true, length = 32)
    private byte[] codeHash;
    @Column(name = "code")
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(name = "input_tool", nullable = false, length = 32)
    private TreasureHuntInputTool inputTool = TreasureHuntInputTool.KEYPAD;
    @OneToMany(mappedBy = "checkpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<TreasureHuntContent> contents = new ArrayList<>();
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TreasureHuntPath getPath() { return path; }
    public void setPath(TreasureHuntPath path) { this.path = path; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public byte[] getCodeHash() { return codeHash; }
    public void setCodeHash(byte[] codeHash) { this.codeHash = codeHash; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public TreasureHuntInputTool getInputTool() { return inputTool; }
    public void setInputTool(TreasureHuntInputTool inputTool) { this.inputTool = inputTool; }
    public List<TreasureHuntContent> getContents() { return contents; }
    public void addContent(TreasureHuntContent content) { content.setCheckpoint(this); content.setVault(null); contents.add(content); }
}
