package fr.insastronaute.webapi.push.controller;

import fr.insastronaute.webapi.push.dto.PushPublicKeyDto;
import fr.insastronaute.webapi.push.dto.PushSubscriptionRequest;
import fr.insastronaute.webapi.push.service.PushKeyProvider;
import fr.insastronaute.webapi.push.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'abonnement d'un appareil aux notifications, application fermee.
 *
 * <p>Ces routes sont ouvertes aux comptes non encore valides, contrairement au reste de
 * l'API. C'est deliberé et c'est le seul moyen de tenir la promesse la plus utile du
 * push : prevenir quelqu'un que <strong>son compte vient d'etre valide</strong>. Ce
 * moment-la est precisement celui ou il a ferme l'application, parfois depuis des
 * heures ; il faut donc qu'il ait pu s'abonner avant. Abonner son propre appareil a ses
 * propres notifications ne divulgue rien et n'accorde rien.</p>
 */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Push")
public class PushController {

    private final PushSubscriptionService subscriptions;
    private final PushKeyProvider keys;

    public PushController(PushSubscriptionService subscriptions, PushKeyProvider keys) {
        this.subscriptions = subscriptions;
        this.keys = keys;
    }

    @GetMapping("/public-key")
    @Operation(
            summary = "VAPID public key",
            description = "Returns the application server key the browser needs, and whether push is enabled at all."
    )
    public PushPublicKeyDto publicKey() {
        return keys.publicKey();
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Register a device",
            description = "Idempotent on the endpoint: renewing the browser keys updates the device instead of adding one."
    )
    public void subscribe(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody PushSubscriptionRequest request,
            HttpServletRequest servletRequest
    ) {
        subscriptions.subscribe(
                oidcUser.getSubject(),
                request.endpoint(),
                request.p256dh(),
                request.auth(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unregister a device", description = "Knowing the endpoint is enough: at worst one stops receiving.")
    public void unsubscribe(@RequestParam String endpoint) {
        subscriptions.unsubscribe(endpoint);
    }
}
