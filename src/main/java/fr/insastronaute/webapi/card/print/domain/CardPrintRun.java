package fr.insastronaute.webapi.card.print.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "card_print_runs")
public class CardPrintRun {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private CardPrintTemplate template;

    @Column(name = "sheet_count", nullable = false)
    private Integer sheetCount;

    @Column(name = "instance_count", nullable = false)
    private Integer instanceCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardPrintRunStatus status = CardPrintRunStatus.COMPLETED;

    @Column(name = "pdf_object_key", length = 512)
    private String pdfObjectKey;

    @Column(name = "pdf_url", length = 1024)
    private String pdfUrl;

    @OneToMany(mappedBy = "printRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pageIndex ASC, slotIndex ASC")
    private List<CardPrintRunSlot> slots = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
    public CardPrintTemplate getTemplate() { return template; }
    public void setTemplate(CardPrintTemplate template) { this.template = template; }
    public Integer getSheetCount() { return sheetCount; }
    public void setSheetCount(Integer sheetCount) { this.sheetCount = sheetCount; }
    public Integer getInstanceCount() { return instanceCount; }
    public void setInstanceCount(Integer instanceCount) { this.instanceCount = instanceCount; }
    public CardPrintRunStatus getStatus() { return status; }
    public void setStatus(CardPrintRunStatus status) { this.status = status; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public void setPdfObjectKey(String pdfObjectKey) { this.pdfObjectKey = pdfObjectKey; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public List<CardPrintRunSlot> getSlots() { return slots; }
    public void addSlot(CardPrintRunSlot slot) {
        slot.setPrintRun(this);
        slots.add(slot);
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
