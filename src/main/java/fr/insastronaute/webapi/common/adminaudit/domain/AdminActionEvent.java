package fr.insastronaute.webapi.common.adminaudit.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du registre : qui a fait quoi, a qui, et quand.
 *
 * <p>Aucun setter : la table est en ajout seul, et une ligne modifiable ne
 * vaudrait rien comme preuve.</p>
 *
 * <p>L'auteur et la cible sont tous deux facultatifs, pour deux raisons
 * differentes : voir {@link #getActor()} et {@link #getTarget()}.</p>
 */
@Entity
@Table(name = "admin_action_events")
public class AdminActionEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * L'administrateur auteur - absent seulement sur une ligne reprise dont le
     * moderateur d'origine n'a pas pu etre retrouve. La reprise associe une
     * decision passee a son auteur en re-hachant le {@code keycloak_sub} de
     * chaque utilisateur ; un moderateur parti, dont la ligne {@code users} a
     * disparu, ne hache plus vers rien. Une telle decision est reprise avec un
     * auteur inconnu plutot qu'omise : une ligne sans auteur reste une
     * information, son absence n'en est pas une.
     *
     * <p>Une ligne ecrite en direct, elle, a toujours un auteur :
     * {@code record} et {@code recordGlobal} le resolvent ou echouent. Seul
     * {@code recordBackfilled} accepte son absence, et il marque justement la
     * ligne {@code backfilled}. C'est la meme regle que porte la contrainte de
     * schema, qui n'autorise l'absence d'auteur que sur une ligne reprise.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private AppUser actor;

    /**
     * Le joueur touche - absent sur les gestes de portee globale, qui ont un
     * auteur et N joueurs touches et nomment donc leur portee plutot qu'une
     * personne. La contrainte {@code ck_admin_action_events_target_presence}
     * tient les deux sens : obligatoire sur les types nominatifs, interdite
     * sur les types globaux.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", updatable = false)
    private AppUser target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AdminActionType action;

    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String detailsJson;

    @Column(nullable = false, updatable = false)
    private boolean backfilled;

    protected AdminActionEvent() {}

    /**
     * Une ligne ecrite en direct : elle est datee de l'horloge murale.
     *
     * <p><strong>Et deliberement pas de {@code TimeTravelContext.instantNow()},
     * contrairement a tout le reste du projet.</strong> Le voyage dans le temps
     * existe pour eprouver un comportement qui depend de la date, et partout
     * ailleurs c'est la bonne horloge. Ici, non : {@code TimeTravelFilter}
     * laisse n'importe quelle requete portant {@code ROLE_ADMIN} ou
     * {@code ROLE_STAFF} fixer cette horloge a l'instant de son choix, par
     * l'en-tete {@code X-Time-Travel-Date}. La date d'une ligne du registre
     * serait alors choisie par la personne meme que le registre existe pour
     * tenir comptable.</p>
     *
     * <p>Ce n'est pas theorique : un administrateur qui se crediterait 5000
     * tokens sous {@code X-Time-Travel-Date: 2020-01-01T00:00:00Z} ecrirait une
     * ligne datee de 2020. Dans un registre trie du plus recent au plus ancien,
     * elle tombe sous la derniere page que quiconque fera defiler - invisible,
     * sans que rien n'ait ete supprime. Et c'est le mensonge le plus difficile a
     * reperer : l'auteur, la cible et les montants sont exacts ; seule la date
     * ment.</p>
     *
     * <p>Donc : ne pas "reparer" cette incoherence en revenant a
     * {@code TimeTravelContext}. L'instant force, lui, n'est pas perdu pour
     * autant - {@code AdminActionJournal} le porte dans les details quand il y
     * en a un, ou il est une information sur le geste et non sa date.</p>
     */
    public AdminActionEvent(AppUser actor, AppUser target, AdminActionType action, String detailsJson, boolean backfilled) {
        this(Instant.now(), actor, target, action, detailsJson, backfilled);
    }

    /**
     * Une ligne dont la date est un <strong>fait sur le passe</strong> et non
     * l'instant de l'ecriture : une reprise, qui redate un geste anterieur au
     * registre, ou un montage de test qui a besoin de lignes a des dates
     * choisies.
     *
     * <p>Cette porte existe parce que l'horloge murale est fausse pour une
     * reprise, exactement comme l'horloge voyagee l'est pour une ligne vivante.
     * Elle n'est pas une echappatoire pour dater une ligne vivante : passer
     * {@code backfilled = false} avec un instant qui ne vient pas de
     * {@code Instant.now()} rouvre le trou que le constructeur ci-dessus
     * ferme.</p>
     */
    public AdminActionEvent(
            Instant occurredAt, AppUser actor, AppUser target, AdminActionType action, String detailsJson, boolean backfilled) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.target = target;
        this.action = action;
        this.detailsJson = detailsJson;
        this.backfilled = backfilled;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    /** L'auteur, ou {@code null} sur une ligne reprise sans moderateur retrouvable. */
    public AppUser getActor() { return actor; }
    /** Le joueur touche, ou {@code null} sur un geste de portee globale. */
    public AppUser getTarget() { return target; }
    public AdminActionType getAction() { return action; }
    public String getDetailsJson() { return detailsJson; }
    public boolean isBackfilled() { return backfilled; }
}
