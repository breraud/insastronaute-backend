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
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "treasure_hunt_contents")
public class TreasureHuntContent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id")
    private TreasureHuntCheckpoint checkpoint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id")
    private TreasureHuntVault vault;
    @Column(nullable = false)
    private Integer position;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreasureHuntContentType type;
    @Column(name = "content_text", columnDefinition = "text")
    private String text;
    @Column(name = "storage_path", length = 1024)
    private String storagePath;
    @Column(name = "article_id")
    private UUID articleId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TreasureHuntCheckpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(TreasureHuntCheckpoint checkpoint) { this.checkpoint = checkpoint; }
    public void setVault(TreasureHuntVault vault) { this.vault = vault; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public TreasureHuntContentType getType() { return type; }
    public void setType(TreasureHuntContentType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public UUID getArticleId() { return articleId; }
    public void setArticleId(UUID articleId) { this.articleId = articleId; }
}
