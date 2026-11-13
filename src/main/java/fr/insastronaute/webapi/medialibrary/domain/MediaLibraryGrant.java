package fr.insastronaute.webapi.medialibrary.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Le droit d'ouvrir la Mediatheque, donne a une personne par un administrateur.
 *
 * <p>Une ligne ne suffit pas : il faut aussi porter un role de staff, ce que
 * {@code MediaLibraryAccess} verifie a chaque requete.</p>
 */
@Entity
@Table(name = "media_library_grants")
public class MediaLibraryGrant {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false)
    private AppUser grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected MediaLibraryGrant() {
    }

    public MediaLibraryGrant(AppUser user, AppUser grantedBy, Instant grantedAt) {
        this.userId = user.getId();
        this.user = user;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public UUID getUserId() { return userId; }
    public AppUser getUser() { return user; }
    public AppUser getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
}
