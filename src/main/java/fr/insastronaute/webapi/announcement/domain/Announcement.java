package fr.insastronaute.webapi.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une annonce affichee aux joueurs.
 *
 * <p>Elle vit dans une fenetre plutot que d'etre allumee puis oubliee : une panne
 * annoncee resterait sinon a l'ecran une semaine apres sa resolution. {@code active}
 * permet de la couper avant terme sans perdre son texte.</p>
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementKind kind = AnnouncementKind.INFO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    /**
     * L'onglet vers lequel l'annonce renvoie, ou {@code null}.
     *
     * <p>Un chemin interne : une annonce s'ouvre chez tous les joueurs, elle ne doit
     * pas pouvoir les emmener hors du site.</p>
     */
    @Column(length = 120)
    private String link;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    /** L'annonce est-elle affichable maintenant ? */
    public boolean isVisibleAt(Instant now) {
        return active
                && (startDate == null || !startDate.isAfter(now))
                && (endDate == null || !endDate.isBefore(now));
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public AnnouncementKind getKind() { return kind; }
    public void setKind(AnnouncementKind kind) { this.kind = kind == null ? AnnouncementKind.INFO : kind; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
