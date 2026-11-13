package fr.insastronaute.webapi.place.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "place_reset_outbox")
public class PlaceResetOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 19)
    private String revision;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private int attempts;

    protected PlaceResetOutbox() {}
    public PlaceResetOutbox(long revision) {
        this.revision = Long.toString(revision);
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }
    public Long getId() { return id; }
    public String getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { attempts++; }
}
