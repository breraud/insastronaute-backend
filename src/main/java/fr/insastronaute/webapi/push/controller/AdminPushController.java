package fr.insastronaute.webapi.push.controller;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.push.dto.PushTestResultDto;
import fr.insastronaute.webapi.push.service.PushSubscriptionService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * De quoi verifier que la chaine de push fonctionne, sur son propre telephone.
 *
 * <p><strong>L'envoi vise toujours l'appelant, jamais quelqu'un d'autre.</strong> Une
 * route qui pousserait un texte libre vers un compte choisi serait un porte-voix : le
 * jour ou elle fuit, ou le jour ou quelqu'un se trompe de destinataire, deux mille
 * telephones sonnent. Se tester soi-meme suffit a repondre a la seule question qui se
 * pose - est-ce que ca marche de bout en bout - et ne repond a aucune autre.</p>
 *
 * <p>La notification est une vraie notification : elle s'ecrit en base, part dans la
 * file, et sort par le depileur comme les autres. Un raccourci qui appellerait
 * directement le service de push testerait autre chose que ce qui tourne en
 * production.</p>
 */
@RestController
@RequestMapping("/api/admin/push")
@Tag(name = "Admin Push")
public class AdminPushController {

    private final NotificationService notifications;
    private final PushSubscriptionService subscriptions;
    private final UserRepository users;

    public AdminPushController(
            NotificationService notifications,
            PushSubscriptionService subscriptions,
            UserRepository users
    ) {
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.users = users;
    }

    @GetMapping("/devices")
    @Operation(
            summary = "How many devices of yours are subscribed",
            description = "Read after a test to see the purge: a device that no longer exists is only removed when a "
                    + "send to it fails, so the count drops one dispatch after the fact, not at the moment it vanished."
    )
    public PushTestResultDto devices(@AuthenticationPrincipal OidcUser oidcUser) {
        long devices = subscriptions.countFor(oidcUser.getSubject());
        return new PushTestResultDto(devices, devices == 0
                ? "Aucun appareil abonné."
                : devices + " appareil(s) abonné(s).");
    }

    @PostMapping("/test")
    @Operation(
            summary = "Send yourself a test notification",
            description = "Writes a real notification for the caller and lets it travel the whole chain. "
                    + "Always targets the caller: a route that pushed free text to a chosen account would be a megaphone."
    )
    public PushTestResultDto sendTest(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser caller = users.findByKeycloakSub(oidcUser.getSubject())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        long devices = subscriptions.countFor(oidcUser.getSubject());

        notifications.notifyUser(
                caller.getId(),
                NotificationType.PUSH_TEST,
                "Test Cédric",
                "Si tu lis ceci sur ton écran verrouillé, la chaîne de notifications fonctionne.",
                Map.of("test", "true")
        );

        // Le compte est celui d'avant l'envoi, et il ne peut pas etre autre chose : le
        // depileur passe une seconde ou deux plus tard, et c'est lui qui apprendra du
        // service de push qu'un appareil a disparu. D'ou GET /devices, que le panel relit
        // apres coup pour montrer la purge au lieu de la laisser deviner.
        return new PushTestResultDto(devices, devices == 0
                ? "Notification écrite, mais aucun appareil abonné : rien ne partira tant que tu n'as pas activé les notifications dans l'application joueur."
                : "Envoi demandé vers " + devices + " appareil(s) connu(s). Les appareils disparus sont retirés au moment de l'envoi.");
    }
}
