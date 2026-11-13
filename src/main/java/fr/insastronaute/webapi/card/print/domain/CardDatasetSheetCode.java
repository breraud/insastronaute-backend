package fr.insastronaute.webapi.card.print.domain;

import fr.insastronaute.webapi.card.domain.Card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Un code imprime a une position donnee d'une planche.
 *
 * <p>C'est aussi l'identite de la <strong>bande physique</strong> : une position d'une
 * planche est un bout de papier unique, qui sera photographie plusieurs fois. La separation
 * entrainement/validation se fait sur cette identite-la, jamais sur l'image.
 */
@Entity
@Table(
        name = "card_dataset_sheet_codes",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_card_dataset_sheet_codes_position",
                columnNames = {"sheet_id", "strip_position"}
        )
)
public class CardDatasetSheetCode {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CardDatasetSheet sheet;

    @Column(name = "strip_position", nullable = false)
    private Integer position;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    /**
     * La carte tiree sous ce code.
     *
     * <p>Nulle pour les planches generees avant que cette memoire existe : leur affectation
     * carte -> emplacement est perdue, donc elles ne peuvent pas etre re-rendues.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CardDatasetSheet getSheet() { return sheet; }
    public void setSheet(CardDatasetSheet sheet) { this.sheet = sheet; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }
}
