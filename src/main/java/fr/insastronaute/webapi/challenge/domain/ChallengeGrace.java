package fr.insastronaute.webapi.challenge.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Le droit rendu a un joueur de reposter une preuve sur un defi ferme.
 *
 * <p>Un refus n'a jamais empeche de recommencer. Ce qui empeche, c'est la
 * fenetre du defi : une preuve envoyee la veille de la fermeture et lue le
 * lendemain est refusee alors qu'il ne reste plus une minute pour la refaire.
 * Le joueur a fait le defi, il a mal cadre sa photo, et il n'a aucun recours.</p>
 *
 * <p>La grace rouvre cette fenetre, pour ce joueur et ce defi seulement, et pour
 * le temps qu'on lui accorde. Elle ne rouvre pas un defi qui n'a pas commence :
 * c'est du temps rendu, pas un passe-droit.</p>
 */
@Entity
@Table(name = "challenge_graces")
public class ChallengeGrace {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** La soumission refusee a l'origine de la grace, si elle existe encore. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private ChallengeSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private AppUser grantedBy;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        }
    }

    public UUID getId() {
        return id;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public ChallengeSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(ChallengeSubmission submission) {
        this.submission = submission;
    }

    public AppUser getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(AppUser grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
