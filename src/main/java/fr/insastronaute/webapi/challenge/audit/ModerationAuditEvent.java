package fr.insastronaute.webapi.challenge.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_audit_events")
public class ModerationAuditEvent {
    @Id private UUID id;
    @Column(name="submission_id", nullable=false) private UUID submissionId;
    @Column(name="moderator_hash", nullable=false, length=64) private String moderatorHash;
    @Column(name="event_type", nullable=false, length=40) private String eventType;
    @Column(nullable=false, length=20) private String outcome;
    @Column(name="metadata_json", nullable=false, columnDefinition="TEXT") private String metadataJson;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    /**
     * La mention « la soumission n'existe plus ».
     *
     * <p>Posee par le code avant la suppression, parce que la base ne sait pas
     * estampiller ce qu'elle cascade. Nulle tant que la soumission vit : sans ce
     * champ, une ligne orpheline ne se distingue pas d'une ligne vivante.</p>
     */
    @Column(name="submission_deleted_at") private Instant submissionDeletedAt;

    protected ModerationAuditEvent() {}
    public ModerationAuditEvent(UUID submissionId, String moderatorHash, String eventType, String outcome, String metadataJson) {
        this(UUID.randomUUID(), submissionId, moderatorHash, eventType, outcome, metadataJson);
    }
    /**
     * <p><strong>La date vient de l'horloge murale, et deliberement pas de
     * {@code TimeTravelContext.instantNow()}</strong> - contrairement au reste
     * du projet, et pour la meme raison que
     * {@link fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent} :
     * {@code TimeTravelFilter} laisse n'importe quelle requete portant
     * {@code ROLE_ADMIN} ou {@code ROLE_STAFF} fixer cette horloge par
     * l'en-tete {@code X-Time-Travel-Date}. Cette table est la trace des
     * decisions de moderation et des lectures de preuves : sa date serait alors
     * choisie par la personne meme qu'elle existe pour tenir comptable.</p>
     *
     * <p>Deux consequences la rendent plus lourde qu'un simple doublon du
     * probleme. La reprise {@code ChallengeDecisionBackfill} copie
     * {@code created_at} dans {@code occurred_at} du registre : une decision
     * antidatee ici deviendrait une ligne antidatee la-bas, sous la derniere
     * page que quiconque fera defiler. Et le sondage anti-doublon de cette
     * meme reprise rapproche les deux tables a une minute pres : deux horloges
     * qui divergent lui feraient ecrire un doublon indelebile dans une table
     * qui n'accepte aucune suppression.</p>
     *
     * <p>Donc : ne pas "reparer" cette incoherence en revenant a
     * {@code TimeTravelContext}.</p>
     */
    public ModerationAuditEvent(UUID id, UUID submissionId, String moderatorHash, String eventType, String outcome, String metadataJson) {
        this.id=id; this.submissionId=submissionId; this.moderatorHash=moderatorHash;
        this.eventType=eventType; this.outcome=outcome; this.metadataJson=metadataJson; this.createdAt=Instant.now();
    }
    public UUID submissionId() { return submissionId; }
    public Instant submissionDeletedAt() { return submissionDeletedAt; }
    public String moderatorHash() { return moderatorHash; }
    public String eventType() { return eventType; }
    public String outcome() { return outcome; }
    public String metadataJson() { return metadataJson; }
    public Instant createdAt() { return createdAt; }
}
