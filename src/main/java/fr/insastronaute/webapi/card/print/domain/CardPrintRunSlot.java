package fr.insastronaute.webapi.card.print.domain;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
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
@Table(name = "card_print_run_slots")
public class CardPrintRunSlot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "print_run_id", nullable = false)
    private CardPrintRun printRun;

    @Column(name = "page_index", nullable = false)
    private Integer pageIndex;

    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_instance_id", nullable = false)
    private CardInstance cardInstance;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CardPrintRun getPrintRun() { return printRun; }
    public void setPrintRun(CardPrintRun printRun) { this.printRun = printRun; }
    public Integer getPageIndex() { return pageIndex; }
    public void setPageIndex(Integer pageIndex) { this.pageIndex = pageIndex; }
    public Integer getSlotIndex() { return slotIndex; }
    public void setSlotIndex(Integer slotIndex) { this.slotIndex = slotIndex; }
    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }
    public CardInstance getCardInstance() { return cardInstance; }
    public void setCardInstance(CardInstance cardInstance) { this.cardInstance = cardInstance; }
}
