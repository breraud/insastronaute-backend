package fr.insastronaute.webapi.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * Ce sur quoi on compte les appels.
     *
     * <p>{@link #SUBJECT} impute a un compte : c'est le bon choix des qu'il y en a un, car
     * il suit le joueur d'un reseau a l'autre. {@link #CLIENT_IP} sert la ou il n'y a pas
     * de compte a qui imputer quoi que ce soit — une route ouverte a tous.</p>
     */
    private enum KeySource { SUBJECT, CLIENT_IP }

    private static final List<RoutePolicy> ROUTE_POLICIES = List.of(
            // ── Les secrets qu'on devine ─────────────────────────────────────
            // Ce qui protege ces routes n'est pas la longueur du code mais le nombre
            // d'essais qu'on laisse faire.

            /*
             * Le passage prioritaire est le seul secret partage de l'application, et il
             * s'essaie sans etre authentifie : le limiteur par sujet ne le voyait donc
             * jamais, et un code court se cassait a la vitesse du lien. Il se compte par
             * adresse, faute de compte a qui l'imputer.
             *
             * Dix essais par dix minutes : personne ne se trompe dix fois de suite en
             * collant un code, et un attaquant tombe a mille quatre cents essais par jour.
             */
            route(HttpMethod.POST, "/api/auth/gate/fast-pass", KeySource.CLIENT_IP,
                    new RateLimitPolicy("fast-pass-attempt", 10, Duration.ofMinutes(10))),
            // Les leurres de l'etabli consomment des appels comme les vrais outils : un
            // joueur qui explore ne doit pas epuiser son quota avant d'avoir tente quoi
            // que ce soit. La force brute reste hors de portee -- douze notes, longueur
            // inconnue.
            route(HttpMethod.POST, "/api/treasure-hunt/checkpoints/validate",
                    new RateLimitPolicy("checkpoint-validation", 30, Duration.ofMinutes(1))),
            // Le coffre prend un verrou pessimiste sur le joueur avant de comparer six
            // codes : l'essayer en boucle coute au serveur, meme sans espoir de le forcer.
            route(HttpMethod.POST, "/api/treasure-hunt/vault/unlock",
                    new RateLimitPolicy("vault-unlock", 10, Duration.ofMinutes(1))),
            // Une carte se revendique par son code imprime. Vingt par minute laissent de
            // quoi enchainer un paquet entier au scanner sans jamais gener personne.
            route(HttpMethod.POST, "/api/cards/instances/claim",
                    new RateLimitPolicy("card-claim", 20, Duration.ofMinutes(1))),

            // ── Ce qui coute ─────────────────────────────────────────────────
            route(HttpMethod.GET, "/api/friends/search",
                    new RateLimitPolicy("friend-search", 30, Duration.ofMinutes(1))),
            route(HttpMethod.GET, "/api/friends/contacts/search",
                    new RateLimitPolicy("friend-search", 30, Duration.ofMinutes(1))),
            route(HttpMethod.POST, "/api/friends/requests/{userId}",
                    new RateLimitPolicy("friend-request", 10, Duration.ofHours(1))),
            route(HttpMethod.POST, "/api/uploads/proofs/direct",
                    new RateLimitPolicy("proof-upload-reservation", 20, Duration.ofMinutes(1))),
            route(HttpMethod.POST, "/api/uploads/proofs/direct/batch",
                    new RateLimitPolicy("proof-upload-reservation", 20, Duration.ofMinutes(1))),
            // Une soumission porte des medias et declenche la validation : elle pese plus
            // qu'un clic. Vingt par minute couvrent un defi photo repris plusieurs fois.
            route(HttpMethod.POST, "/api/challenges/{challengeId}/submit",
                    new RateLimitPolicy("challenge-submission", 20, Duration.ofMinutes(1))),
            // La route la plus sollicitee au moment d'une annonce est aussi celle qui se
            // serialise sur un verrou pessimiste : sans frein, un client qui reessaie apres
            // trois secondes d'attente allonge lui-meme la file ou il patiente. Dix par
            // minute laissent largement de quoi corriger une reponse au questionnaire.
            route(HttpMethod.POST, "/api/events/{eventId}/registrations",
                    new RateLimitPolicy("event-registration", 10, Duration.ofMinutes(1))),
            route(HttpMethod.POST, "/api/shop/orders",
                    new RateLimitPolicy("shop-order", 5, Duration.ofMinutes(1))),
            // Le panier se manipule au bouton « plus » : la limite doit laisser cliquer.
            // Soixante par minute, c'est un clic par seconde sans interruption.
            route(HttpMethod.PUT, "/api/shop/cart/items/{productId}",
                    new RateLimitPolicy("cart-change", 60, Duration.ofMinutes(1))),
            route(HttpMethod.DELETE, "/api/shop/cart/items/{productId}",
                    new RateLimitPolicy("cart-change", 60, Duration.ofMinutes(1))),
            // Un appel Keycloak par compte : sur deux mille joueurs, deux relances de suite
            // suffisent a mettre Keycloak a genoux. Le balayage nocturne fait le meme travail.
            route(HttpMethod.POST, "/api/admin/users/department-resync",
                    new RateLimitPolicy("department-resync-sweep", 2, Duration.ofHours(1))),
            route(HttpMethod.POST, "/api/admin/batches/dataset/sheets",
                    new RateLimitPolicy("dataset-sheet-generation", 10, Duration.ofHours(1))),

            // Une page de la Mediatheque affiche jusqu'a 240 vignettes : la limite doit laisser
            // defiler. Le fichier complet, plus lourd, se compte a part.
            route(HttpMethod.GET, "/api/media-library/media/{submissionId}/{index}/thumbnail",
                    new RateLimitPolicy("media-library-thumbnail", 600, Duration.ofMinutes(1))),
            route(HttpMethod.GET, "/api/media-library/media/{submissionId}/{index}/content",
                    new RateLimitPolicy("media-library-content", 120, Duration.ofMinutes(1)))
    );

    private final RedisRateLimiter rateLimiter;

    public RateLimitInterceptor(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RoutePolicy routePolicy = policyFor(request);
        if (routePolicy == null) {
            return true;
        }

        String key = key(routePolicy.keySource(), request);
        if (key == null) {
            return true;
        }

        rateLimiter.requireAllowed(key, routePolicy.policy());
        return true;
    }

    /**
     * Ce sur quoi la route compte, ou null quand il n'y a rien a compter.
     *
     * <p>L'adresse est prefixee : sans cela, une adresse et un identifiant Keycloak
     * partageraient le meme espace de cles, et un sujet qui ressemblerait a une adresse
     * viderait le compteur de quelqu'un d'autre.</p>
     */
    private static String key(KeySource source, HttpServletRequest request) {
        if (source == KeySource.CLIENT_IP) {
            String address = request.getRemoteAddr();
            return address == null || address.isBlank() ? null : "ip:" + address;
        }
        return authenticatedSubject();
    }

    private static RoutePolicy policyFor(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return ROUTE_POLICIES.stream()
                .filter(route -> route.method().matches(request.getMethod()))
                .filter(route -> route.path().matches(path))
                .findFirst()
                .orElse(null);
    }

    private static String authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? null : name;
    }

    private static RoutePolicy route(HttpMethod method, String path, RateLimitPolicy policy) {
        return route(method, path, KeySource.SUBJECT, policy);
    }

    private static RoutePolicy route(HttpMethod method, String path, KeySource keySource, RateLimitPolicy policy) {
        return new RoutePolicy(method, PathPatternParser.defaultInstance.parse(path), keySource, policy);
    }

    private record RoutePolicy(HttpMethod method, PathPattern path, KeySource keySource, RateLimitPolicy policy) {
    }
}
