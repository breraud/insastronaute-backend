package fr.insastronaute.webapi.card.print.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une planche de dataset imprimee, et les codes qu'elle porte.
 *
 * <p>C'est la verite terrain de la seance de scan : sans elle, une planche photographiee
 * n'est pas etiquetable, on a les images et pas les codes. Le {@code sheetId} est ce qui
 * est imprime sur le papier et retape par un humain.
 */
@Entity
@Table(name = "card_dataset_sheets")
public class CardDatasetSheet {

    @Id
    private UUID id;

    @Column(name = "sheet_id", nullable = false, unique = true, length = 16)
    private String sheetId;

    /** Renseigne quand la planche vient d'un vrai lot d'impression, nul pour une planche de test. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "print_run_id")
    private CardPrintRun printRun;

    /** Cle S3 du PDF original. Nulle pour les planches creees avant sa conservation. */
    @Column(name = "pdf_object_key", length = 512)
    private String pdfObjectKey;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<CardDatasetSheetCode> codes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSheetId() { return sheetId; }
    public void setSheetId(String sheetId) { this.sheetId = sheetId; }
    public CardPrintRun getPrintRun() { return printRun; }
    public void setPrintRun(CardPrintRun printRun) { this.printRun = printRun; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public void setPdfObjectKey(String pdfObjectKey) { this.pdfObjectKey = pdfObjectKey; }
    public List<CardDatasetSheetCode> getCodes() { return codes; }
    public void addCode(CardDatasetSheetCode code) {
        code.setSheet(this);
        codes.add(code);
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
