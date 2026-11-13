package fr.insastronaute.webapi.common.ratelimit;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dit au demarrage ce que le limiteur ne protege pas.
 *
 * <p>Deux configurations le mettent hors service sans que rien ne le signale, et
 * l'application demarre normalement dans les deux cas. Elles ne se voient qu'en essayant
 * de depasser une limite qui n'existe plus — c'est-a-dire jamais, jusqu'au jour ou
 * quelqu'un le fait pour de bon.</p>
 */
@Component
public class RateLimitStartupReport {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitStartupReport.class);

    private final boolean rateLimitingActive;
    private final String forwardHeadersStrategy;

    // Designe explicitement : le second constructeur n'existe que pour les tests, et
    // Spring refuse de choisir entre deux candidats sans annotation.
    @Autowired
    public RateLimitStartupReport(
            ObjectProvider<RateLimitInterceptor> interceptor,
            @Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy
    ) {
        this(interceptor.getIfAvailable() != null, forwardHeadersStrategy);
    }

    RateLimitStartupReport(boolean rateLimitingActive, String forwardHeadersStrategy) {
        this.rateLimitingActive = rateLimitingActive;
        this.forwardHeadersStrategy = forwardHeadersStrategy;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        warnings().forEach(LOGGER::error);
    }

    /** Ce qui manque, en clair, ou rien quand tout est en place. */
    List<String> warnings() {
        List<String> warnings = new ArrayList<>();

        if (!rateLimitingActive) {
            // Le limiteur ne s'installe que si un StringRedisTemplate existe. Sans Redis,
            // il n'est pas enregistre du tout : aucune route n'est freinee, et rien
            // d'autre ne le dit.
            warnings.add("Aucune limitation de debit : Redis est absent, le limiteur n'est pas installe. "
                    + "Toutes les routes, y compris le passage prioritaire, acceptent un nombre illimite d'appels.");
            return warnings;
        }

        if (!trustsProxyHeaders()) {
            // Les routes anonymes se comptent par adresse. Derriere un proxy qui n'est pas
            // declare, getRemoteAddr() rend celle du proxy : tous les clients partagent
            // alors un seul compteur, et le premier a l'epuiser ferme la route aux autres.
            warnings.add("server.forward-headers-strategy=" + forwardHeadersStrategy
                    + " : les limites par adresse compteront l'adresse du proxy, pas celle du client. "
                    + "Derriere un reverse proxy, poser SERVER_FORWARD_HEADERS_STRATEGY=framework.");
        }

        return warnings;
    }

    private boolean trustsProxyHeaders() {
        return forwardHeadersStrategy != null && !"none".equalsIgnoreCase(forwardHeadersStrategy.trim());
    }
}
