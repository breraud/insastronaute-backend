package fr.insastronaute.webapi.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interaso.webpush.WebPush;
import com.interaso.webpush.WebPushService;
import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.notification.repository.NotificationRepository;
import fr.insastronaute.webapi.push.domain.PushOutboxEntry;
import fr.insastronaute.webapi.push.domain.PushSubscription;
import fr.insastronaute.webapi.push.repository.PushOutboxRepository;
import fr.insastronaute.webapi.push.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Limit;

/**
 * Le depileur : il sort les envois de la file et les fait, loin des requetes.
 *
 * <p>C'est ici que le reseau intervient, et nulle part ailleurs. Le detour par une
 * table sert exactement a ca : la transaction qui accorde un succes ou annule un
 * evenement depose une ligne et rend la main, et un service de push injoignable ne peut
 * ni la faire echouer ni la ralentir. Sans cette separation, annuler un evenement a
 * deux cents inscrits ferait deux cents appels HTTPS en serie sur le fil de la requete
 * de l'administrateur.</p>
 *
 * <p>Trois issues par ligne, et une seule est un echec :</p>
 * <ul>
 *   <li>envoi accepte : la ligne part, l'abonnement est date ;</li>
 *   <li>{@code SubscriptionState.EXPIRED} - le service a repondu 404 ou 410 : l'appareil
 *       n'existe plus, <strong>l'abonnement est supprime</strong>. C'est la seule purge,
 *       et sans elle la table gonfle de telephones reinitialises que chaque envoi
 *       repaie ;</li>
 *   <li>panne : la ligne reste, son compteur monte, on reessaiera. Au-dela du plafond
 *       elle est abandonnee - un service qui refuse durablement ne changera pas d'avis
 *       parce qu'on insiste.</li>
 * </ul>
 *
 * <p>Le bean n'existe que si le push est configure : sans lui, la file se remplit sans
 * etre depilee, ce qui est le bon comportement pour un push qu'on rallume.</p>
 */
@Component
@ConditionalOnBean(WebPushService.class)
public class PushDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushDispatcher.class);

    /** Le chiffrement plafonne le corps autour de 4 ko : on tronque plutot que d'echouer. */
    private static final int MAX_BODY_LENGTH = 400;

    private final PushOutboxRepository outbox;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final WebPushService webPush;
    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public PushDispatcher(
            PushOutboxRepository outbox,
            PushSubscriptionRepository subscriptions,
            NotificationRepository notifications,
            WebPushService webPush,
            PushProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.outbox = outbox;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.webPush = webPush;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.push.dispatch-delay-ms:2000}")
    public void dispatchPending() {
        List<PushOutboxEntry> pending = outbox.findByOrderByCreatedAtAsc(Limit.of(properties.getBatchSize()));
        for (PushOutboxEntry entry : pending) {
            try {
                transactions.executeWithoutResult(status -> deliver(entry));
            } catch (RuntimeException | Error failure) {
                // Une ligne qui explose ne doit pas emporter le lot : les suivantes
                // n'ont rien a voir avec elle.
                LOGGER.warn("Envoi push {} en echec", entry.getNotificationId(), failure);
                transactions.executeWithoutResult(status -> recordFailure(entry));
            }
        }
    }

    /**
     * Un envoi, dans sa transaction.
     *
     * <p>Elle passe par un {@code TransactionTemplate} et non par {@code @Transactional} :
     * la methode est appelee depuis {@code dispatchPending}, donc depuis la meme classe,
     * et une annotation ne s'y appliquerait pas - le proxy Spring n'intercepte pas les
     * appels internes. Les suppressions passeraient quand meme, chaque methode de depot
     * ouvrant la sienne, mais {@code markDelivered} ne serait jamais ecrit : l'entite ne
     * serait attachee a aucun contexte de persistance.</p>
     */
    private void deliver(PushOutboxEntry entry) {
        Optional<Notification> found = notifications.findById(entry.getNotificationId());
        if (found.isEmpty()) {
            // La notification a ete supprimee entre-temps : il n'y a plus rien a pousser.
            outbox.delete(entry);
            return;
        }

        Notification notification = found.get();
        List<PushSubscription> devices = subscriptions.findByUserId(notification.getRecipient().getId());
        String payload = payloadOf(notification);

        for (PushSubscription device : devices) {
            // Java n'a pas les arguments par defaut de Kotlin : ttl, topic et urgence
            // doivent etre passes explicitement, nuls faute de raison d'en choisir.
            WebPush.SubscriptionState state = webPush.send(
                    payload, device.getEndpoint(), device.getP256dh(), device.getAuth(), null, null, null);
            if (state == WebPush.SubscriptionState.EXPIRED) {
                LOGGER.info("Abonnement push expire, supprime : {}", device.getId());
                subscriptions.delete(device);
            } else {
                device.markDelivered(Instant.now());
            }
        }
        outbox.delete(entry);
    }

    private void recordFailure(PushOutboxEntry entry) {
        outbox.findById(entry.getId()).ifPresent(current -> {
            current.incrementAttempts();
            if (current.getAttempts() >= properties.getMaxAttempts()) {
                LOGGER.warn("Envoi push {} abandonne apres {} tentatives",
                        current.getNotificationId(), current.getAttempts());
                outbox.delete(current);
            }
        });
    }

    /**
     * Ce que le service worker recevra.
     *
     * <p>L'{@code url} est le point du chantier qui se voit : sans elle, un clic sur la
     * notification ouvre l'accueil et l'etudiant cherche ce dont on vient de le
     * prevenir.</p>
     */
    private String payloadOf(Notification notification) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", notification.getTitle());
        body.put("body", truncated(notification.getBody()));
        body.put("type", String.valueOf(notification.getType()));
        // Le regroupement se decide ici, pas dans le service worker, parce que c'est ici
        // qu'on sait ce que la notification dit. Une etiquette par notification : deux
        // notifications qui partagent la leur se remplacent l'une l'autre, et la premiere
        // disparait sans un mot. Regrouper par type semblait eviter d'empiler dix
        // bannieres ; ca faisait surtout perdre neuf informations sur dix, et rendait
        // tout envoi de test invisible apres le premier.
        body.put("tag", String.valueOf(notification.getId()));
        body.put("url", targetUrl(notification));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Payload push illisible", exception);
        }
    }

    private static String truncated(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_BODY_LENGTH ? value : value.substring(0, MAX_BODY_LENGTH - 1) + "…";
    }

    private static String targetUrl(Notification notification) {
        return switch (notification.getType()) {
            case CHALLENGE_SUBMISSION_VALIDATED, CHALLENGE_SUBMISSION_REJECTED,
                 CHALLENGE_GROUP_INVITE_RECEIVED, CHALLENGE_GROUP_INVITE_ACCEPTED,
                 CHALLENGE_GROUP_INVITE_DECLINED, CHALLENGE_GRACE_GRANTED,
                 CHALLENGE_BONUS_ADJUSTED, CHALLENGE_GROUP_MEMBER_REMOVED -> "/defis";
            case FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED,
                 GROUP_INVITE_RECEIVED, GROUP_JOIN_REQUEST -> "/amis";
            case EVENT_CANCELLED, EVENT_REGISTRATION_CANCELLED -> "/evenements";
            case TOURNAMENT_POINTS_AWARDED -> "/classements";
            case PUSH_TEST -> "/parametres";
        };
    }
}
