package fr.insastronaute.webapi.push.service;

import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le service d'envoi, monte seulement si le push est configure.
 *
 * <p>Le drapeau ne suffit pas : un push active sans cles VAPID enverrait des piles
 * d'erreurs a chaque passage du planificateur, sans que personne ne comprenne pourquoi
 * rien n'arrive. Le demarrage echoue donc franchement, comme
 * {@code ProductionSecurityValidator} le fait pour l'URL du front et le domaine de
 * cookie - un reglage a moitie fait est le pire des trois etats.</p>
 *
 * <p>Push eteint, aucun bean n'existe et le dispatcher ne demarre pas : le reste de
 * l'application ne s'en apercoit pas, les notifications continuent de s'ecrire en base
 * et de s'afficher dans l'application.</p>
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
    public WebPushService webPushService(PushProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "app.push.enabled=true exige app.push.subject, app.push.vapid-public-key et "
                            + "app.push.vapid-private-key. Un push active sans cles n'envoie rien et ne le dit pas.");
        }
        VapidKeys keys = VapidKeys.create(properties.getVapidPublicKey(), properties.getVapidPrivateKey());
        LOGGER.info("Push web actif pour {}", properties.getSubject());
        return new WebPushService(properties.getSubject(), keys);
    }
}
