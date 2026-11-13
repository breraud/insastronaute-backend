package fr.insastronaute.webapi.push.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.push.domain.PushSubscription;
import fr.insastronaute.webapi.push.repository.PushSubscriptionRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Ce que le navigateur enregistre et retire : un appareil, ses cles, son proprietaire. */
@Service
public class PushSubscriptionService {

    private static final int MAX_ENDPOINT_LENGTH = 2048;

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions, UserRepository users) {
        this.subscriptions = subscriptions;
        this.users = users;
    }

    /**
     * Enregistre l'appareil, ou remet a jour celui qui porte deja cet endpoint.
     *
     * <p>Le navigateur peut renouveler ses cles sans changer d'endpoint, et un endpoint
     * peut changer de proprietaire - un telephone prete, un compte partage sur un poste
     * commun. Dans les deux cas c'est le meme appareil : on met a jour au lieu
     * d'empiler, sinon l'ancien proprietaire continuerait de recevoir les notifications
     * du nouveau.</p>
     */
    @Transactional
    public void subscribe(String keycloakSub, String endpoint, String p256dh, String auth, String userAgent) {
        if (endpoint == null || endpoint.isBlank() || p256dh == null || p256dh.isBlank()
                || auth == null || auth.isBlank()) {
            throw new BadRequestException("Abonnement incomplet.");
        }
        if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new BadRequestException("Endpoint d'abonnement trop long.");
        }

        AppUser user = currentUser(keycloakSub);
        subscriptions.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> {
                    existing.refreshKeys(p256dh, auth, trimmed(userAgent));
                    if (!existing.getUser().getId().equals(user.getId())) {
                        subscriptions.delete(existing);
                        subscriptions.flush();
                        subscriptions.save(new PushSubscription(user, endpoint, p256dh, auth, trimmed(userAgent)));
                    }
                },
                () -> subscriptions.save(new PushSubscription(user, endpoint, p256dh, auth, trimmed(userAgent)))
        );
    }

    /**
     * Retire l'appareil.
     *
     * <p>Sans verifier a qui il appartient, deliberement : connaitre un endpoint suffit
     * a se desabonner, et c'est sans danger - au pire on cesse de recevoir. Exiger le
     * proprietaire empecherait un navigateur de nettoyer un abonnement qu'il vient de
     * renouveler, ce qui est le cas courant.</p>
     */
    @Transactional
    public void unsubscribe(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Endpoint requis.");
        }
        subscriptions.deleteByEndpoint(endpoint);
    }

    @Transactional(readOnly = true)
    public long countFor(String keycloakSub) {
        return subscriptions.countByUserId(currentUser(keycloakSub).getId());
    }

    private AppUser currentUser(String keycloakSub) {
        return users.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }

    private static String trimmed(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
