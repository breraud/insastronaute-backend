package fr.insastronaute.webapi.push.service;

import fr.insastronaute.webapi.push.domain.PushOutboxEntry;
import fr.insastronaute.webapi.push.repository.PushOutboxRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Le seul point par lequel une notification entre dans la file de push.
 *
 * <p>Il est appele depuis {@code NotificationService.notifyUser}, dans la meme
 * transaction que l'ecriture de la notification : les deux vivent ou meurent ensemble.
 * Aucun reseau ici - c'est tout l'interet du detour par une table.</p>
 *
 * <p>La file se remplit meme quand le push est eteint, et c'est voulu : le dispatcher,
 * lui, ne demarre pas, donc les lignes s'accumulent et seront envoyees si on l'allume.
 * Un push eteint pendant une heure ne perd donc rien. Si l'accumulation devenait un
 * probleme, ce serait au dispatcher de purger ce qui est trop vieux, pas a l'ecriture
 * de decider.</p>
 */
@Component
public class PushEnqueuer {

    private final PushOutboxRepository outbox;

    public PushEnqueuer(PushOutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void enqueue(UUID notificationId) {
        if (notificationId == null) {
            return;
        }
        outbox.save(new PushOutboxEntry(notificationId));
    }
}
