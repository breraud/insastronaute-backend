package fr.insastronaute.webapi.place.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un plan partage avec quelqu'un.
 *
 * <p>La ligne retient qui a partage : un plan qui apparait sans qu'on sache de
 * qui il vient ressemble a une erreur.</p>
 */
@Entity
@Table(name = "place_blueprint_shares")
@IdClass(PlaceBlueprintShare.Key.class)
public class PlaceBlueprintShare {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id", nullable = false)
    private PlaceBlueprint blueprint;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_user_id")
    private AppUser sharedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        }
    }

    public PlaceBlueprint getBlueprint() { return blueprint; }
    public void setBlueprint(PlaceBlueprint blueprint) { this.blueprint = blueprint; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AppUser getSharedBy() { return sharedBy; }
    public void setSharedBy(AppUser sharedBy) { this.sharedBy = sharedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public static class Key implements Serializable {
        private UUID blueprint;
        private UUID user;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(blueprint, key.blueprint) && Objects.equals(user, key.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blueprint, user);
        }
    }
}
