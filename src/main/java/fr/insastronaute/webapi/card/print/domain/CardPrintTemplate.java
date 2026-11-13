package fr.insastronaute.webapi.card.print.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "card_print_templates")
public class CardPrintTemplate {

    public static final int SLOT_COUNT = 9;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "pdf_object_key", nullable = false, length = 512)
    private String pdfObjectKey;

    @Column(name = "pdf_url", nullable = false, length = 1024)
    private String pdfUrl;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Column(name = "page_width_pt", nullable = false)
    private Double pageWidthPt;

    @Column(name = "page_height_pt", nullable = false)
    private Double pageHeightPt;

    @Column(nullable = false)
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_mode", nullable = false, length = 20)
    private CardPrintFitMode fitMode = CardPrintFitMode.COVER;

    @Column(name = "bleed_pt", nullable = false)
    private Double bleedPt = 0D;

    @Column(name = "code_x_pt", nullable = false)
    private Double codeXPt = 0D;

    @Column(name = "code_y_pt", nullable = false)
    private Double codeYPt = 0D;

    @Column(name = "code_width_pt", nullable = false)
    private Double codeWidthPt = 120D;

    @Column(name = "code_height_pt", nullable = false)
    private Double codeHeightPt = 14D;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_font_family", nullable = false, length = 30)
    private CardPrintFontFamily codeFontFamily = CardPrintFontFamily.HELVETICA;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_font_weight", nullable = false, length = 20)
    private CardPrintFontWeight codeFontWeight = CardPrintFontWeight.BOLD;

    @Column(name = "code_font_size_pt", nullable = false)
    private Double codeFontSizePt = 9D;

    @Column(name = "code_color", nullable = false, length = 7)
    private String codeColor = "#111111";

    @Column(name = "code_background_color", length = 7)
    private String codeBackgroundColor;

    @Column(name = "code_background_opacity", nullable = false)
    private Double codeBackgroundOpacity = 0D;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_align", nullable = false, length = 20)
    private CardPrintTextAlign codeAlign = CardPrintTextAlign.CENTER;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("slotIndex ASC")
    private List<CardPrintTemplateSlot> slots = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("slotIndex ASC")
    private List<CardPrintTemplateBackSlot> backSlots = new ArrayList<>();

    @Column(name = "qr_enabled", nullable = false)
    private boolean qrEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_corner", nullable = false, length = 20)
    private CardPrintQrCorner qrCorner = CardPrintQrCorner.BOTTOM_RIGHT;

    @Column(name = "qr_size_pt", nullable = false)
    private Double qrSizePt = 32D;

    @Column(name = "qr_margin_modules", nullable = false)
    private Integer qrMarginModules = 4;

    @Column(name = "qr_inset_pt", nullable = false)
    private Double qrInsetPt = 4D;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public void setPdfObjectKey(String pdfObjectKey) { this.pdfObjectKey = pdfObjectKey; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Double getPageWidthPt() { return pageWidthPt; }
    public void setPageWidthPt(Double pageWidthPt) { this.pageWidthPt = pageWidthPt; }
    public Double getPageHeightPt() { return pageHeightPt; }
    public void setPageHeightPt(Double pageHeightPt) { this.pageHeightPt = pageHeightPt; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public CardPrintFitMode getFitMode() { return fitMode; }
    public void setFitMode(CardPrintFitMode fitMode) { this.fitMode = fitMode == null ? CardPrintFitMode.COVER : fitMode; }
    public Double getBleedPt() { return bleedPt; }
    public void setBleedPt(Double bleedPt) { this.bleedPt = bleedPt == null ? 0D : bleedPt; }
    public Double getCodeXPt() { return codeXPt; }
    public void setCodeXPt(Double codeXPt) { this.codeXPt = codeXPt; }
    public Double getCodeYPt() { return codeYPt; }
    public void setCodeYPt(Double codeYPt) { this.codeYPt = codeYPt; }
    public Double getCodeWidthPt() { return codeWidthPt; }
    public void setCodeWidthPt(Double codeWidthPt) { this.codeWidthPt = codeWidthPt; }
    public Double getCodeHeightPt() { return codeHeightPt; }
    public void setCodeHeightPt(Double codeHeightPt) { this.codeHeightPt = codeHeightPt; }
    public CardPrintFontFamily getCodeFontFamily() { return codeFontFamily; }
    public void setCodeFontFamily(CardPrintFontFamily codeFontFamily) {
        this.codeFontFamily = codeFontFamily == null ? CardPrintFontFamily.HELVETICA : codeFontFamily;
    }
    public CardPrintFontWeight getCodeFontWeight() { return codeFontWeight; }
    public void setCodeFontWeight(CardPrintFontWeight codeFontWeight) {
        this.codeFontWeight = codeFontWeight == null ? CardPrintFontWeight.BOLD : codeFontWeight;
    }
    public Double getCodeFontSizePt() { return codeFontSizePt; }
    public void setCodeFontSizePt(Double codeFontSizePt) { this.codeFontSizePt = codeFontSizePt; }
    public String getCodeColor() { return codeColor; }
    public void setCodeColor(String codeColor) { this.codeColor = codeColor; }
    public String getCodeBackgroundColor() { return codeBackgroundColor; }
    public void setCodeBackgroundColor(String codeBackgroundColor) { this.codeBackgroundColor = codeBackgroundColor; }
    public Double getCodeBackgroundOpacity() { return codeBackgroundOpacity; }
    public void setCodeBackgroundOpacity(Double codeBackgroundOpacity) { this.codeBackgroundOpacity = codeBackgroundOpacity; }
    public CardPrintTextAlign getCodeAlign() { return codeAlign; }
    public void setCodeAlign(CardPrintTextAlign codeAlign) {
        this.codeAlign = codeAlign == null ? CardPrintTextAlign.CENTER : codeAlign;
    }
    public List<CardPrintTemplateSlot> getSlots() { return slots; }
    public void setSlots(List<CardPrintTemplateSlot> slots) {
        this.slots.clear();
        if (slots != null) {
            slots.forEach(this::addSlot);
        }
    }
    public void addSlot(CardPrintTemplateSlot slot) {
        slot.setTemplate(this);
        this.slots.add(slot);
    }
    public List<CardPrintTemplateBackSlot> getBackSlots() { return backSlots; }
    public void setBackSlots(List<CardPrintTemplateBackSlot> backSlots) {
        this.backSlots.clear();
        if (backSlots != null) backSlots.forEach(this::addBackSlot);
    }
    public void addBackSlot(CardPrintTemplateBackSlot slot) {
        slot.setTemplate(this);
        this.backSlots.add(slot);
    }
    public boolean isQrEnabled() { return qrEnabled; }
    public void setQrEnabled(boolean qrEnabled) { this.qrEnabled = qrEnabled; }
    public CardPrintQrCorner getQrCorner() { return qrCorner; }
    public void setQrCorner(CardPrintQrCorner qrCorner) {
        this.qrCorner = qrCorner == null ? CardPrintQrCorner.BOTTOM_RIGHT : qrCorner;
    }
    public Double getQrSizePt() { return qrSizePt; }
    public void setQrSizePt(Double qrSizePt) { this.qrSizePt = qrSizePt == null ? 32D : qrSizePt; }
    public Integer getQrMarginModules() { return qrMarginModules; }
    public void setQrMarginModules(Integer qrMarginModules) { this.qrMarginModules = qrMarginModules == null ? 4 : qrMarginModules; }
    public Double getQrInsetPt() { return qrInsetPt; }
    public void setQrInsetPt(Double qrInsetPt) { this.qrInsetPt = qrInsetPt == null ? 4D : qrInsetPt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
