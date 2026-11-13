package fr.insastronaute.webapi.challenge.auto.event;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * La seule couture par laquelle les services métier alimentent les défis
 * automatiques. Volontairement mince : l'appelant énonce ce qui vient d'arriver
 * et à qui, sans rien savoir des défis.
 *
 * <p>Chacun annonce à sa propre granularité : un pack de cinq cartes appelle une
 * fois par carte, un défi de groupe une fois par membre. L'écouteur travaillant
 * sur l'état déjà commité, ces répétitions referaient les mêmes requêtes pour la
 * même réponse. On ne garde donc qu'un événement par (joueur, moment) et par
 * transaction.
 */
@Component
public class AutoChallengeTriggerPublisher {

    private final ApplicationEventPublisher publisher;

    public AutoChallengeTriggerPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(UUID userId, AutoChallengeTrigger trigger) {
        if (userId == null || trigger == null) {
            return;
        }
        AutoChallengeTriggerEvent event = new AutoChallengeTriggerEvent(userId, trigger);
        if (isNewInThisTransaction(event)) {
            publisher.publishEvent(event);
        }
    }

    public void publishAll(Collection<UUID> userIds, AutoChallengeTrigger trigger) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(userId -> publish(userId, trigger));
    }

    /**
     * L'ensemble des vus vit dans une synchronisation plutôt que dans une
     * ressource liée : le gestionnaire de transaction les abandonne à la fin et
     * les suspend autour d'un REQUIRES_NEW, si bien que l'ensemble ne peut ni
     * fuir sur un fil du pool ni déborder dans une transaction interne.
     */
    private boolean isNewInThisTransaction(AutoChallengeTriggerEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Rien à quoi rattacher la déduplication. Publier est la réponse sûre :
            // filtrer ici pourrait perdre la seule annonce de ce fait.
            return true;
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PublishedTriggers published) {
                return published.isNew(event);
            }
        }
        PublishedTriggers published = new PublishedTriggers();
        TransactionSynchronizationManager.registerSynchronization(published);
        return published.isNew(event);
    }

    /** Porte l'ensemble des vus de la transaction. Ne prend aucune part au commit. */
    private static final class PublishedTriggers implements TransactionSynchronization {

        private final Set<AutoChallengeTriggerEvent> seen = new HashSet<>();

        private boolean isNew(AutoChallengeTriggerEvent event) {
            return seen.add(event);
        }
    }
}
