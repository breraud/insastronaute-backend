package fr.insastronaute.webapi.card.print.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une decoupe photographiee et son etiquette.
 *
 * <p>L'image stockee est la <strong>decoupe brute</strong>, telle qu'elle sort de la
 * segmentation : ni redimensionnee, ni mise en gris, ni normalisee. Le pretraitement
 * s'applique a l'entrainement, depuis cette image ; le jour ou il change, tout se re-derive
 * sans re-scanner de papier.
 */
@Entity
@Table(name = "card_dataset_crops")
public class CardDatasetCrop {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CardDatasetSheet sheet;

    /** La bande physique dont vient cette photo. C'est par elle que se fait la separation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strip_id", nullable = false)
    private CardDatasetSheetCode strip;

    @Column(name = "label", length = 32)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CardDatasetCropStatus status = CardDatasetCropStatus.PENDING_ARBITRATION;

    /**
     * Comment cette decoupe a ete cadree. Le defaut est {@link CardDatasetCropFraming#AUTOMATIC} :
     * les outils de collecte qui ne disent rien passent par la segmentation automatique, et un
     * cadrage inconnu melange aux cas faciles rendrait la colonne inexploitable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "framing", nullable = false, length = 16)
    private CardDatasetCropFraming framing = CardDatasetCropFraming.AUTOMATIC;

    @Column(name = "image_object_key", nullable = false, length = 512)
    private String imageObjectKey;

    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "image_byte_size")
    private Long imageByteSize;

    @Column(name = "image_width_px")
    private Integer imageWidthPx;

    @Column(name = "image_height_px")
    private Integer imageHeightPx;

    @Column(name = "capture_device", length = 200)
    private String captureDevice;

    @Column(name = "capture_width_px")
    private Integer captureWidthPx;

    @Column(name = "capture_height_px")
    private Integer captureHeightPx;

    /**
     * La photo entiere d'ou la decoupe a ete prise, et la region qu'elle y occupait.
     *
     * <p>Sans ces deux-la on garde la reponse de l'operateur et on jette la question : une
     * decoupe cadree a la main est un echec de la segmentation corrige par un humain, et c'est
     * l'exemple qui permettrait de la reparer -- a condition de savoir ou la boite se trouvait.
     *
     * <p>La region est exprimee dans les coordonnees de {@link #frameObjectKey}, l'image
     * stockee, et non dans celles du flux ni du capteur. Un seul repere : une mise a l'echelle
     * oubliee decalerait sinon chaque boite du jeu sans que rien ne le signale.
     *
     * <p><strong>Attention pour l'entrainement :</strong> une photo contient plusieurs bandes et
     * une seule est etiquetee. Les regions non decrites ne sont pas du fond, elles sont
     * inconnues -- les compter comme du fond apprendrait a un detecteur a ignorer de vraies
     * bandes.
     */
    @Column(name = "frame_object_key", length = 512)
    private String frameObjectKey;

    @Column(name = "frame_content_type", length = 100)
    private String frameContentType;

    @Column(name = "frame_byte_size")
    private Long frameByteSize;

    @Column(name = "frame_width_px")
    private Integer frameWidthPx;

    @Column(name = "frame_height_px")
    private Integer frameHeightPx;

    @Column(name = "region_x_px")
    private Integer regionXPx;

    @Column(name = "region_y_px")
    private Integer regionYPx;

    @Column(name = "region_width_px")
    private Integer regionWidthPx;

    @Column(name = "region_height_px")
    private Integer regionHeightPx;

    @Column(name = "region_angle_deg")
    private Double regionAngleDeg;

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
    public CardDatasetSheet getSheet() { return sheet; }
    public void setSheet(CardDatasetSheet sheet) { this.sheet = sheet; }
    public CardDatasetSheetCode getStrip() { return strip; }
    public void setStrip(CardDatasetSheetCode strip) { this.strip = strip; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public CardDatasetCropStatus getStatus() { return status; }
    public void setStatus(CardDatasetCropStatus status) { this.status = status; }
    public CardDatasetCropFraming getFraming() { return framing; }
    public void setFraming(CardDatasetCropFraming framing) {
        this.framing = framing == null ? CardDatasetCropFraming.AUTOMATIC : framing;
    }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public String getImageContentType() { return imageContentType; }
    public void setImageContentType(String imageContentType) { this.imageContentType = imageContentType; }
    public Long getImageByteSize() { return imageByteSize; }
    public void setImageByteSize(Long imageByteSize) { this.imageByteSize = imageByteSize; }
    public Integer getImageWidthPx() { return imageWidthPx; }
    public void setImageWidthPx(Integer imageWidthPx) { this.imageWidthPx = imageWidthPx; }
    public Integer getImageHeightPx() { return imageHeightPx; }
    public void setImageHeightPx(Integer imageHeightPx) { this.imageHeightPx = imageHeightPx; }
    public String getFrameObjectKey() { return frameObjectKey; }
    public void setFrameObjectKey(String frameObjectKey) { this.frameObjectKey = frameObjectKey; }
    public String getFrameContentType() { return frameContentType; }
    public void setFrameContentType(String frameContentType) { this.frameContentType = frameContentType; }
    public Long getFrameByteSize() { return frameByteSize; }
    public void setFrameByteSize(Long frameByteSize) { this.frameByteSize = frameByteSize; }
    public Integer getFrameWidthPx() { return frameWidthPx; }
    public void setFrameWidthPx(Integer frameWidthPx) { this.frameWidthPx = frameWidthPx; }
    public Integer getFrameHeightPx() { return frameHeightPx; }
    public void setFrameHeightPx(Integer frameHeightPx) { this.frameHeightPx = frameHeightPx; }
    public Integer getRegionXPx() { return regionXPx; }
    public void setRegionXPx(Integer regionXPx) { this.regionXPx = regionXPx; }
    public Integer getRegionYPx() { return regionYPx; }
    public void setRegionYPx(Integer regionYPx) { this.regionYPx = regionYPx; }
    public Integer getRegionWidthPx() { return regionWidthPx; }
    public void setRegionWidthPx(Integer regionWidthPx) { this.regionWidthPx = regionWidthPx; }
    public Integer getRegionHeightPx() { return regionHeightPx; }
    public void setRegionHeightPx(Integer regionHeightPx) { this.regionHeightPx = regionHeightPx; }
    public Double getRegionAngleDeg() { return regionAngleDeg; }
    public void setRegionAngleDeg(Double regionAngleDeg) { this.regionAngleDeg = regionAngleDeg; }
    public String getCaptureDevice() { return captureDevice; }
    public void setCaptureDevice(String captureDevice) { this.captureDevice = captureDevice; }
    public Integer getCaptureWidthPx() { return captureWidthPx; }
    public void setCaptureWidthPx(Integer captureWidthPx) { this.captureWidthPx = captureWidthPx; }
    public Integer getCaptureHeightPx() { return captureHeightPx; }
    public void setCaptureHeightPx(Integer captureHeightPx) { this.captureHeightPx = captureHeightPx; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
