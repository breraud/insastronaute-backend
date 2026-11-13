package fr.insastronaute.webapi.common.adminaudit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Le seul endroit qui ecrit dans le registre.
 *
 * <p>L'auteur est passe explicitement par l'appelant plutot que lu dans le
 * contexte de securite : c'est la seule forme ou oublier de journaliser un
 * geste est une erreur de compilation, et non une ligne "auteur inconnu"
 * decouverte six mois plus tard.</p>
 */
@Service
public class AdminActionJournal {

    private static final List<String> FORBIDDEN_METADATA = List.of("proof", "token", "cookie", "range", "url");

    /**
     * La date que l'administrateur avait forcee au moment du geste, quand il en
     * avait force une.
     *
     * <p>La ligne est datee de l'horloge murale
     * ({@link AdminActionEvent}), et c'est la seule date juste : un geste a eu
     * lieu quand il a eu lieu. Mais ses <em>effets</em>, eux, ont ete calcules
     * contre la date factice - une fenetre de defi, une expiration, un
     * calendrier. Sans cette cle, un lecteur six mois plus tard verrait un
     * resultat inexplicable et n'aurait aucun moyen d'apprendre que le geste
     * s'est joue contre une autre date. C'est une information sur le geste qui
     * n'existe nulle part ailleurs, et un fait non ecrit dans une table en
     * ajout seul est un fait perdu.</p>
     *
     * <p>Le nom evite les cinq fragments que {@link #rejectSensitiveDetailKeys}
     * refuse.</p>
     */
    static final String TIME_TRAVEL_DETAIL_KEY = "timeTravelDate";

    private final AdminActionEventRepository events;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public AdminActionJournal(AdminActionEventRepository events, UserRepository users, ObjectMapper objectMapper) {
        this.events = events;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    /**
     * Un geste nominatif : un auteur, un joueur touche.
     *
     * <p>Un type de portee globale est refuse ici. La contrainte
     * {@code ck_admin_action_events_target_presence} le rattraperait a
     * l'insertion, mais echouer en Java donne a l'appelant une erreur
     * comprehensible plutot qu'une violation de contrainte.</p>
     */
    public void record(String actorKeycloakSub, UUID targetUserId, AdminActionType action, Map<String, ?> details) {
        if (action.isGlobalScope()) {
            throw new IllegalArgumentException(
                    "L'action " + action + " est de portee globale : elle s'ecrit avec recordGlobal, sans joueur cible.");
        }
        AppUser actor = users.findByKeycloakSub(actorKeycloakSub)
                .orElseThrow(() -> new NotFoundException("Auteur de l'action introuvable."));
        AppUser target = users.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Joueur cible introuvable."));

        events.save(new AdminActionEvent(actor, target, action, serializeLiveGesture(details), false));
    }

    /**
     * Un geste de portee globale : un auteur, une portee, et le nombre de
     * joueurs touches porte par les details. Pas de cible a resoudre.
     *
     * <p>Un type nominatif est refuse ici, pour la meme raison que l'inverse
     * l'est dans {@link #record}.</p>
     */
    public void recordGlobal(String actorKeycloakSub, AdminActionType action, Map<String, ?> details) {
        if (!action.isGlobalScope()) {
            throw new IllegalArgumentException(
                    "L'action " + action + " nomme un joueur : elle s'ecrit avec record, avec une cible.");
        }
        AppUser actor = users.findByKeycloakSub(actorKeycloakSub)
                .orElseThrow(() -> new NotFoundException("Auteur de l'action introuvable."));

        events.save(new AdminActionEvent(actor, null, action, serializeLiveGesture(details), false));
    }

    /**
     * Une ligne reprise apres coup, sur un geste anterieur au registre.
     *
     * <p>Troisieme porte vers la table, donc meme refus que {@link #record} :
     * sans lui, un type de portee globale passerait ici avec une cible et ne
     * serait rattrape que par la contrainte
     * {@code ck_admin_action_events_target_presence}. Aucun appelant de
     * production ne l'emprunte encore ; c'est precisement le bon moment pour
     * fermer la porte, la table etant en ajout seul.</p>
     *
     * <p>{@code occurredAt} est <strong>obligatoire</strong>, et c'est la date
     * du geste d'origine, pas celle de la reprise. Une ligne vivante se date
     * de l'horloge murale parce qu'elle est ecrite au moment du geste ; une
     * ligne reprise, elle, est ecrite des mois plus tard, et l'horloge murale y
     * serait aussi fausse que l'horloge voyagee l'est sur une ligne vivante.
     * Le parametre est explicite plutot que documente : un appelant qui
     * apparaitrait un jour ne peut pas oublier une date qu'il est force de
     * fournir, et la table etant en ajout seul, une ligne mal datee ne se
     * repare jamais. (La reprise reelle,
     * {@code ChallengeDecisionBackfill}, n'emprunte pas cette porte : elle
     * ecrit en JDBC depuis une migration Flyway, ou aucun bean n'existe.)</p>
     */
    public void recordBackfilled(
            Instant occurredAt, AppUser actor, AppUser target, AdminActionType action, Map<String, ?> details) {
        if (action.isGlobalScope()) {
            throw new IllegalArgumentException(
                    "L'action " + action + " est de portee globale : elle s'ecrit avec recordGlobal, sans joueur cible.");
        }
        events.save(new AdminActionEvent(occurredAt, actor, target, action, serialize(details), true));
    }

    /**
     * La garde de {@code ModerationAuditService}, reprise telle quelle : un
     * registre consultable ne doit pas devenir un endroit ou fuient des URL
     * signees.
     *
     * <p>Publique et statique parce que la table a deux portes d'ecriture qui
     * ne partagent pas de code : ce service, et la reprise du passe, qui ecrit
     * en JDBC depuis une migration Flyway et n'a donc aucun bean sous la main.
     * Une seconde copie de la liste serait une seconde occasion de la laisser
     * diverger.</p>
     */
    public static void rejectSensitiveDetailKeys(Map<String, ?> details) {
        details.keySet().forEach(key -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_METADATA.stream().anyMatch(normalized::contains)) {
                throw new IllegalArgumentException("Metadonnee de journal sensible interdite : " + key);
            }
        });
    }

    /**
     * Les details d'un geste vivant, augmentes de la date forcee quand
     * l'administrateur en avait force une - voir
     * {@link #TIME_TRAVEL_DETAIL_KEY}. Sans date forcee, la carte passe
     * inchangee : une cle presente et nulle se lirait comme une information.
     */
    private String serializeLiveGesture(Map<String, ?> details) {
        Instant forced = TimeTravelContext.getForcedTime();
        if (forced == null) {
            return serialize(details);
        }
        Map<String, Object> enriched = new LinkedHashMap<>(details);
        enriched.put(TIME_TRAVEL_DETAIL_KEY, forced.toString());
        return serialize(enriched);
    }

    private String serialize(Map<String, ?> details) {
        rejectSensitiveDetailKeys(details);
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de serialiser les details de l'action.", exception);
        }
    }
}
