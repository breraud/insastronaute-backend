package fr.insastronaute.webapi.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import fr.insastronaute.webapi.auth.gate.AuthGateFilter;
import fr.insastronaute.webapi.auth.gate.AuthGateProperties;
import fr.insastronaute.webapi.auth.gate.AuthGateService;
import fr.insastronaute.webapi.auth.sessionreset.StaffSessionResetFilter;
import fr.insastronaute.webapi.auth.sessionreset.StaffSessionResetService;
import fr.insastronaute.webapi.auth.sessionreset.PlayerSessionIndexMigrationFilter;
import fr.insastronaute.webapi.common.security.session.SlidingSessionCookieFilter;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import org.springframework.session.web.http.CookieSerializer;
import fr.insastronaute.webapi.auth.gate.AuthGateTokenService;
import fr.insastronaute.webapi.auth.keycloak.KeycloakDepartmentGroupSynchronizationService;
import fr.insastronaute.webapi.common.security.csrf.CsrfCookieFilter;
import fr.insastronaute.webapi.common.security.oauth.DynamicOidcLogoutSuccessHandler;
import fr.insastronaute.webapi.common.security.oauth.FrontendRedirectUriResolver;
import fr.insastronaute.webapi.common.security.oauth.OAuth2LoginFailure;
import fr.insastronaute.webapi.common.security.oauth.OAuth2LoginRedirectUriCaptureFilter;
import fr.insastronaute.webapi.common.security.oauth.KcActionAuthorizationRequestResolver;
import fr.insastronaute.webapi.common.security.oauth.OAuth2LoginSuccessHandler;
import fr.insastronaute.webapi.common.security.oauth.SynchronizingOidcUserService;
import fr.insastronaute.webapi.common.security.roles.KeycloakRoleMapper;
import fr.insastronaute.webapi.common.security.roles.StudentLimitedAccessFilter;
import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import fr.insastronaute.webapi.onboarding.web.ModeratorGenderGateFilter;
import fr.insastronaute.webapi.onboarding.web.OnboardingGateFilter;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import fr.insastronaute.webapi.site.web.SiteAvailabilityFilter;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);


    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String[] MODERATION_ROLES = {
            "ADMIN",
            "MODERATOR",
            "RESPOS_DEFIS",
            "SOUS_RESPO_DEFIS",
            "RESPO_DEPART",
            "SOUS_RESPO_DEPART"
    };
    private static final String[] MODERATION_HISTORY_ROLES = {
            "ADMIN",
            "MODERATOR",
            "RESPOS_DEFIS",
            "SOUS_RESPO_DEFIS"
    };
    public static final String[] CHALLENGE_ADMIN_ROLES = {
            "ADMIN",
            "RESPOS_DEFIS",
            "SOUS_RESPO_DEFIS"
    };
    public static final String[] ONBOARDING_CANCELLATION_ROLES = {
            "ADMIN",
            "RESPOS_DEFIS"
    };
    /**
     * Revoir les points d'une preuve deja validee.
     *
     * <p>Plus etroit que la moderation elle-meme, et deliberement : juger une preuve
     * qu'on a sous les yeux et reecrire apres coup le resultat d'une decision prise
     * par quelqu'un d'autre ne sont pas le meme pouvoir. Les sous-respos moderent,
     * ils n'ajustent pas -- comme pour l'annulation d'une inscription.</p>
     */
    public static final String[] POINTS_ADJUSTMENT_ROLES = {
            "ADMIN",
            "RESPOS_DEFIS"
    };
    private static final String[] CARD_ADMIN_ROLES = {
            "ADMIN",
            "RESPO_CARTES",
            "SOUS_RESPO_CARTES"
    };
    /**
     * Le catalogue en lecture seule. Les responsables defis configurent des defis
     * automatiques qui designent une categorie de carte : sans cette lecture, le
     * selecteur de categorie reste vide et le defi est inconfigurable. La liste
     * derive de CARD_ADMIN_ROLES plutot que de la recopier, sinon un role ajoute
     * a l'ecriture perdrait la lecture. Les instances physiques n'en font pas
     * partie : elles nomment leurs proprietaires.
     */
    public static final String[] CARD_CATALOG_READ_ROLES = Stream.concat(
            Arrays.stream(CARD_ADMIN_ROLES),
            Arrays.stream(CHALLENGE_ADMIN_ROLES)
    ).distinct().toArray(String[]::new);
    private static final String[] EVENT_ADMIN_ROLES = {
            "ADMIN",
            "RESPOS_EVENT",
            "SOUS_RESPO_EVENT",
            "RESPO_DEPART",
            "SOUS_RESPO_DEPART",
            "RESPO_TOURNOIS",
            "SOUS_RESPO_TOURNOIS"
    };
    private static final String[] SHOP_ADMIN_ROLES = {
            "ADMIN",
            "RESPO_GOODIES",
            "SOUS_RESPO_GOODIES"
    };
    public static final String[] CARD_TEAM_ADMIN_ROLES = {
            "ADMIN",
            "RESPO_CARTES",
            "SOUS_RESPO_CARTES",
            "RESPO_DEPART",
            "SOUS_RESPO_DEPART"
    };
    /**
     * L'encadrement, au sens large. Public parce que la disponibilité du combat de débogage se
     * lit ailleurs (StsDebugAvailabilityController) et doit se lire sur cette liste-ci, pas sur
     * une copie.
     */
    public static final String[] STAFF_ROLES = {
            "ADMIN",
            "MODERATOR",
            "RESPOS_DEFIS",
            "SOUS_RESPO_DEFIS",
            "RESPOS_EVENT",
            "SOUS_RESPO_EVENT",
            "RESPO_CARTES",
            "SOUS_RESPO_CARTES",
            "RESPO_GOODIES",
            "SOUS_RESPO_GOODIES",
            "RESPO_DEPART",
            "SOUS_RESPO_DEPART",
            "RESPO_TOURNOIS",
            "SOUS_RESPO_TOURNOIS"
    };

    /**
     * Qui garde l'acces au site quand il est ferme aux joueurs.
     *
     * <p>Tout le staff, pas seulement les administrateurs : sinon les respos ne
     * peuvent pas preparer leur contenu pendant une coupure. Le PanelAdmin passe
     * donc sans regle qui lui soit propre.</p>
     */
    /**
     * Les roles de staff, sans prefixe. Publics parce que l'annuaire du staff s'en sert
     * pour trier l'affichage : en garder deux copies reviendrait a en avoir une fausse le
     * jour ou l'une bouge.
     */
    public static final Set<String> STAFF_ROLE_NAMES =
            Arrays.stream(STAFF_ROLES).collect(Collectors.toUnmodifiableSet());

    public static final Set<String> SITE_BYPASS_ROLES = Stream.concat(
            Arrays.stream(STAFF_ROLES),
            Stream.of("ACCES_SITE_ADMIN")
    ).collect(Collectors.toUnmodifiableSet());

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            AppSecurityProperties securityProperties,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
            AuthGateTokenService authGateTokenService,
            AuthGateService authGateService,
            AuthGateProperties authGateProperties,
            SiteAvailabilityService siteAvailabilityService,
            StaffSessionResetService staffSessionResetService,
            UserRepository userRepository,
            ModeratorGenderGateService moderatorGenderGateService,
            ObjectProvider<CookieSerializer> cookieSerializer,
            ServerProperties serverProperties,
            ObjectMapper objectMapper
    ) {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/", "/q", "/error", "/logout/success", "/api/public/**", "/api/session", "/api/csrf", "/api/scan", "/api/auth/gate/**").permitAll();
                    if (securityProperties.isOpenapiDocsPublic()) {
                        auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").authenticated();
                    }
                    // L'actuator partage le port de l'application. Sans regle propre il
                    // tombe sur anyRequest().authenticated(), ce qui laisse n'importe quel
                    // joueur valide lire /actuator/prometheus : noms de routes, volumetrie
                    // et internes de la JVM. On garde /health au niveau actuel et on reserve
                    // tout le reste aux administrateurs, y compris ce que la variable
                    // MANAGEMENT_ENDPOINTS pourrait exposer demain sans repasser ici.
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").authenticated()
                            .requestMatchers("/actuator", "/actuator/**").hasRole("ADMIN");

                    // La vue technique est reservee aux administrateurs : elle nomme
                    // chaque route et son taux d'erreur, ce qui dessine l'API entiere.
                    // Le combat de debug reecrit la run de son appelant : points de vie, deck,
                    // reliques et ennemis sont choisis dans la requete, et gagner le combat
                    // termine le noeud et credite les tokens comme n'importe quelle victoire -
                    // rien en aval ne regarde que l'encounter portait encounterId "debug". Le
                    // controleur ne s'expose que sous app.sts.debug.combat.enabled, mais un
                    // drapeau n'est pas une autorisation : tant qu'il est leve, la route etait
                    // ouverte a n'importe quel compte authentifie.
                    auth.requestMatchers("/api/sts/runs/*/debug/**").hasAnyRole(STAFF_ROLES);

                    auth.requestMatchers("/api/admin/health", "/api/admin/health/**").hasRole("ADMIN")
                            // Soldes, noms et gains par joueur : sans cette ligne, la route tombait
                            // sur anyRequest().authenticated() et tout joueur connecte la lisait.
                            .requestMatchers("/api/admin/stats", "/api/admin/stats/**").hasRole("ADMIN")
                            .requestMatchers("/api/admin/shop", "/api/admin/shop/**").hasAnyRole(SHOP_ADMIN_ROLES)
                            .requestMatchers("/api/admin/cards/export", "/api/admin/cards/import").hasRole("ADMIN")
                            // Lecture seule du catalogue, avant la regle d'ecriture qui suit :
                            // seules les listes, et seulement en GET. "/api/admin/cards" sans
                            // etoile, pour ne pas ouvrir /cards/instances avec.
                            .requestMatchers(HttpMethod.GET, "/api/admin/cards", "/api/admin/card-categories", "/api/admin/card-types").hasAnyRole(CARD_CATALOG_READ_ROLES)
                            .requestMatchers("/api/admin/cards", "/api/admin/cards/**", "/api/admin/card-categories", "/api/admin/card-categories/**", "/api/admin/card-types", "/api/admin/card-types/**", "/api/admin/decks", "/api/admin/decks/**", "/api/admin/batches", "/api/admin/batches/**", "/api/admin/card-print-runs", "/api/admin/card-print-runs/**", "/api/admin/card-print-templates", "/api/admin/card-print-templates/**").hasAnyRole(CARD_ADMIN_ROLES)
                            .requestMatchers("/api/admin/department-verification-links", "/api/admin/department-verification-links/**").hasRole("ADMIN")
                            .requestMatchers("/api/admin/challenges", "/api/admin/challenges/**", "/api/admin/challenge-categories", "/api/admin/challenge-categories/**", "/api/admin/qr-link", "/api/admin/qr-link/**", "/api/admin/leaderboards", "/api/admin/leaderboards/**").hasAnyRole(CHALLENGE_ADMIN_ROLES)
                            .requestMatchers("/api/admin/events", "/api/admin/events/**", "/api/admin/event-categories", "/api/admin/event-categories/**", "/api/admin/tournaments", "/api/admin/tournaments/**").hasAnyRole(EVENT_ADMIN_ROLES)
                            .requestMatchers("/api/admin/users", "/api/admin/users/**", "/api/admin/articles", "/api/admin/articles/**", "/api/admin/categories", "/api/admin/categories/**").hasAnyRole(STAFF_ROLES)
                            .requestMatchers("/api/admin/card-teams", "/api/admin/card-teams/**").hasAnyRole(CARD_TEAM_ADMIN_ROLES)
                            // Le rattrapage touche tous les comptes du realm : il doit rester
                            // reserve a ADMIN, et son matcher doit preceder le suivant, plus
                            // general, sous peine de devenir accessible a tout sous-respo defis.
                            .requestMatchers("/api/admin/onboarding/backfill").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/admin/onboarding/*/cancel")
                            .hasAnyRole(ONBOARDING_CANCELLATION_ROLES)
                            .requestMatchers("/api/admin/onboarding", "/api/admin/onboarding/**").hasAnyRole(CHALLENGE_ADMIN_ROLES)
                            .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")
                            // Avant la regle generale de moderation, plus large : ce geste-ci
                            // reecrit une decision deja prise, et ne s'ouvre pas aux memes.
                            .requestMatchers(HttpMethod.POST, "/api/moderation/submissions/*/bonus-points")
                            .hasAnyRole(POINTS_ADJUSTMENT_ROLES)
                            .requestMatchers("/api/moderation/history", "/api/moderation/history/**")
                            .hasAnyRole(MODERATION_HISTORY_ROLES)
                            .requestMatchers("/api/moderation", "/api/moderation/**").hasAnyRole(MODERATION_ROLES)
                            // La Mediatheque ne s'ouvre qu'au staff ; MediaLibraryAccess
                            // exige en plus, a chaque requete, la designation d'un administrateur.
                            .requestMatchers("/api/media-library", "/api/media-library/**").hasAnyRole(STAFF_ROLES)
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated();
                }
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak"),
                                documentationRequestMatcher()
                        )
                        .defaultAuthenticationEntryPointFor(
                                apiAuthenticationEntryPoint(),
                                PathPatternRequestMatcher.pathPattern("/api/**")
                        )
                )
                .addFilterBefore(
                        new OAuth2LoginRedirectUriCaptureFilter(securityProperties),
                        OAuth2AuthorizationRequestRedirectFilter.class
                )
                .addFilterBefore(
                        new AuthGateFilter(authGateTokenService, authGateService, authGateProperties, securityProperties),
                        OAuth2AuthorizationRequestRedirectFilter.class
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(new KcActionAuthorizationRequestResolver(
                                        clientRegistrationRepository, "/oauth2/authorization"))
                        )
                        .tokenEndpoint(token -> token
                                .accessTokenResponseClient(customAccessTokenResponseClient())
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler(new OAuth2LoginSuccessHandler(securityProperties, STAFF_ROLE_NAMES))
                        .failureHandler((request, response, exception) -> {
                            String reason = OAuth2LoginFailure.reasonOf(exception);
                            if (OAuth2LoginFailure.isExpected(reason)) {
                                // Un refus de conditions ou un retour arriere ne sont pas des
                                // pannes : leur donner une trace de cent lignes noie les vraies
                                // le jour ou elles arrivent.
                                LOGGER.warn("Connexion OAuth2 interrompue sur [{}] : {}", request.getRequestURI(), reason);
                            } else {
                                LOGGER.error("OAuth2 login failed for URI [{}] with error: {}", request.getRequestURI(), exception.getMessage(), exception);
                            }
                            String failureRedirectUri = new FrontendRedirectUriResolver(
                                    securityProperties.getFrontendBaseUrl(),
                                    securityProperties.getAllowedOrigins()
                             ).defaultRedirectUri() + "?error=" + reason;
                            response.sendRedirect(failureRedirectUri);
                        })
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository(securityProperties))
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/auth/gate/**")
                )
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(
                        slidingSessionCookieFilter(cookieSerializer, serverProperties),
                        AuthorizationFilter.class
                )
                .addFilterAfter(
                        new StudentLimitedAccessFilter(objectMapper),
                        AuthorizationFilter.class
                )
                .addFilterAfter(
                        new SiteAvailabilityFilter(siteAvailabilityService, objectMapper, SITE_BYPASS_ROLES),
                        AuthorizationFilter.class
                )
                .addFilterAfter(
                        new OnboardingGateFilter(objectMapper, SITE_BYPASS_ROLES),
                        AuthorizationFilter.class
                )
                .addFilterAfter(
                        new ModeratorGenderGateFilter(objectMapper, moderatorGenderGateService),
                        AuthorizationFilter.class
                )
                // Avant l'autorisation, pas apres : une session perimee ne doit pas servir
                // une derniere requete avec les droits qu'on vient justement de lui retirer.
                .addFilterBefore(
                        new StaffSessionResetFilter(staffSessionResetService, objectMapper, STAFF_ROLE_NAMES),
                        AuthorizationFilter.class
                )
                .addFilterBefore(
                        new PlayerSessionIndexMigrationFilter(userRepository, objectMapper, STAFF_ROLE_NAMES),
                        AuthorizationFilter.class
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository, securityProperties))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .build();
    }

    private static AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, ignored) -> {
            new HttpSessionRequestCache().removeRequest(request, response);

            if (isBrowserNavigation(request)) {
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.UNAUTHORIZED.value());
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                request.getRequestDispatcher("/error").forward(request, response);
                return;
            }

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":401,"error":"Unauthorized","message":"Authentication required"}
                    """);
        };
    }

    private static boolean isBrowserNavigation(jakarta.servlet.http.HttpServletRequest request) {
        String fetchMode = request.getHeader("Sec-Fetch-Mode");

        if (fetchMode != null) {
            return "navigate".equalsIgnoreCase(fetchMode);
        }

        String accept = request.getHeader("Accept");

        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static RequestMatcher documentationRequestMatcher() {
        return new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"),
                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                PathPatternRequestMatcher.pathPattern("/v3/api-docs"),
                PathPatternRequestMatcher.pathPattern("/v3/api-docs/**")
        );
    }

    private CookieCsrfTokenRepository csrfTokenRepository(AppSecurityProperties securityProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setHeaderName(CSRF_HEADER_NAME);

        securityProperties.getCookieDomain().ifPresent(cookieDomain ->
                repository.setCookieCustomizer(cookie -> cookie
                        .domain(cookieDomain)
                        .path("/")
                        .secure(true)
                        .httpOnly(false)
                        .sameSite(securityProperties.isProductionHardeningEnabled() ? "Lax" : "None")
                )
        );

        return repository;
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository,
            AppSecurityProperties securityProperties
    ) {
        return new DynamicOidcLogoutSuccessHandler(clientRegistrationRepository, securityProperties);
    }

    /**
     * Le filtre qui fait glisser le cookie de session - quand il y a un cookie a faire
     * glisser.
     *
     * <p>{@link CookieSerializer} n'existe que si Spring Session est actif. Les tests le
     * desactivent ({@code spring.session.store-type=none}) et n'ont donc pas ce bean ; la
     * production l'a. Plutot que d'exiger une dependance absente la moitie du temps, on
     * l'annonce au demarrage : un glissement qui ne s'installe pas en silence serait pire
     * que pas de glissement du tout, puisque personne ne le verrait avant que les sessions
     * ne se mettent a tomber.</p>
     */
    private SlidingSessionCookieFilter slidingSessionCookieFilter(
            ObjectProvider<CookieSerializer> cookieSerializer,
            ServerProperties serverProperties
    ) {
        CookieSerializer serializer = cookieSerializer.getIfAvailable();
        Duration maxAge = serverProperties.getServlet().getSession().getCookie().getMaxAge();

        if (serializer == null) {
            LOGGER.warn("Glissement du cookie de session inactif : aucun bean CookieSerializer. "
                    + "Le plafond du cookie tombera meme sur une session restee active.");
        } else if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            LOGGER.info("Glissement du cookie de session inutile : le cookie n'a pas de duree de vie propre.");
        } else {
            LOGGER.info("Glissement du cookie de session actif : reecriture passe {} d'usage.", maxAge.dividedBy(2));
        }

        return new SlidingSessionCookieFilter(serializer, maxAge);
    }

    @Bean
    KeycloakRoleMapper keycloakRoleMapper(
            @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId
    ) {
        return new KeycloakRoleMapper(clientId);
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(
            KeycloakRoleMapper keycloakRoleMapper,
            KeycloakDepartmentGroupSynchronizationService synchronizationService
    ) {
        return new SynchronizingOidcUserService(new OidcUserService(), keycloakRoleMapper, synchronizationService);
    }

    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper(KeycloakRoleMapper keycloakRoleMapper) {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    Map<String, Object> claims = oidcAuthority.getIdToken().getClaims();

                    mappedAuthorities.addAll(keycloakRoleMapper.extractAuthorities(claims));
                }

                mappedAuthorities.add(authority);
            }

            return mappedAuthorities;
        };
    }

    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        org.springframework.cache.concurrent.ConcurrentMapCache jwkCache = 
                new org.springframework.cache.concurrent.ConcurrentMapCache("jwks");
        
        return clientRegistration -> {
            String jwkSetUri = clientRegistration.getProviderDetails().getJwkSetUri();
            return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .cache(jwkCache)
                    .build();
        };
    }

    private org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest> customAccessTokenResponseClient() {
        org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient client = 
                new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5 seconds connect timeout
        requestFactory.setReadTimeout(20000); // 20 seconds read timeout
        
        RestClient restClient = oauth2TokenRestClient(RestClient.builder().requestFactory(requestFactory));
        
        client.setRestClient(restClient);
        return client;
    }

    static RestClient oauth2TokenRestClient(RestClient.Builder builder) {
        return builder
                .configureMessageConverters(messageConverters -> {
                    messageConverters.addCustomConverter(new FormHttpMessageConverter());
                    messageConverters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
    }
}
