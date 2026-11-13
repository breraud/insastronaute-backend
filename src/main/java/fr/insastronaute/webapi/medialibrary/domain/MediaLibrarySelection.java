package fr.insastronaute.webapi.medialibrary.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un media retenu pour l'after movie. La selection est unique et commune : il n'y a
 * pas de selection par personne.
 *
 * <p>Les lignes s'ecrivent par {@code insertIfAbsent}, jamais par {@code save} : deux
 * personnes qui retiennent le meme media en meme temps ne doivent pas se gener.</p>
 */
@Entity
@Table(name = "media_library_selection")
public class MediaLibrarySelection {

    @EmbeddedId
    private MediaSelectionKey id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_by", nullable = false)
    private AppUser selectedBy;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    protected MediaLibrarySelection() {
    }

    public MediaLibrarySelection(MediaSelectionKey id, AppUser selectedBy, Instant selectedAt) {
        this.id = id;
        this.selectedBy = selectedBy;
        this.selectedAt = selectedAt;
    }

    public MediaSelectionKey getId() { return id; }
    public AppUser getSelectedBy() { return selectedBy; }
    public Instant getSelectedAt() { return selectedAt; }
}
