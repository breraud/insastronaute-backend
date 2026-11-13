package fr.insastronaute.webapi.card.print.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "card_print_template_slots")
public class CardPrintTemplateSlot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private CardPrintTemplate template;

    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    @Column(name = "x_pt", nullable = false)
    private Double xPt;

    @Column(name = "y_pt", nullable = false)
    private Double yPt;

    @Column(name = "width_pt", nullable = false)
    private Double widthPt;

    @Column(name = "height_pt", nullable = false)
    private Double heightPt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CardPrintTemplate getTemplate() { return template; }
    public void setTemplate(CardPrintTemplate template) { this.template = template; }
    public Integer getSlotIndex() { return slotIndex; }
    public void setSlotIndex(Integer slotIndex) { this.slotIndex = slotIndex; }
    public Double getXPt() { return xPt; }
    public void setXPt(Double xPt) { this.xPt = xPt; }
    public Double getYPt() { return yPt; }
    public void setYPt(Double yPt) { this.yPt = yPt; }
    public Double getWidthPt() { return widthPt; }
    public void setWidthPt(Double widthPt) { this.widthPt = widthPt; }
    public Double getHeightPt() { return heightPt; }
    public void setHeightPt(Double heightPt) { this.heightPt = heightPt; }
}
