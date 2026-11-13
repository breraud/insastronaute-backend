package fr.insastronaute.webapi.challenge.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Le code qu'un joueur montre pour qu'on rejoigne son equipe en le scannant.
 *
 * <p>Chercher chaque personne par son nom, lui envoyer une invitation, attendre
 * qu'elle l'accepte : trois gestes et deux ecrans pour quelqu'un qui est assis a
 * cote. Le code remplace les trois.</p>
 *
 * <p>Un seul vivant par personne et par defi, et il expire : un code montre une
 * fois ne doit pas rester une porte ouverte sur l'equipe.</p>
 */
@Entity
@Table(name = "challenge_group_invites")
public class ChallengeGroupInvite {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private UUID token = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitter_user_id", nullable = false)
    private AppUser submitter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = TimeTravelContext.instantNow();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Ceux qui ont deja rejoint en scannant.
     *
     * <p>Ils comptent comme ayant accepte quand la preuve part : ils viennent de
     * dire oui, leur redemander serait la redondance que ce code supprime.</p>
     */
    @ElementCollection
    @CollectionTable(
            name = "challenge_group_invite_joins",
            joinColumns = @JoinColumn(name = "invite_id")
    )
    @Column(name = "user_id", nullable = false)
    private Set<UUID> joinedUserIds = new LinkedHashSet<>();

    public UUID getId() { return id; }
    public UUID getToken() { return token; }
    public void setToken(UUID token) { this.token = token; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public AppUser getSubmitter() { return submitter; }
    public void setSubmitter(AppUser submitter) { this.submitter = submitter; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Set<UUID> getJoinedUserIds() { return joinedUserIds; }

    public boolean expired() {
        return expiresAt == null || expiresAt.isBefore(TimeTravelContext.instantNow());
    }
}
