package fr.insastronaute.webapi.proof.domain;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "proof_drafts")
public class ProofDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", length = 20)
    private ProofType proofType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProofDraftStatus status = ProofDraftStatus.DRAFT;

    /**
     * Les coequipiers choisis pour un defi de groupe.
     *
     * <p>Ils vivaient dans l'etat de la page : un rafraichissement les perdait,
     * quand les fichiers deja deposes, eux, survivaient. Le brouillon garde donc
     * les deux, ou aucun des deux.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "proof_draft_participants",
            joinColumns = @JoinColumn(name = "draft_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<AppUser> participants = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public ProofType getProofType() { return proofType; }
    public void setProofType(ProofType proofType) { this.proofType = proofType; }
    public Set<AppUser> getParticipants() { return participants; }
    public void setParticipants(Set<AppUser> participants) {
        this.participants.clear();
        if (participants != null) {
            this.participants.addAll(participants);
        }
    }
    public ProofDraftStatus getStatus() { return status; }
    public void setStatus(ProofDraftStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
