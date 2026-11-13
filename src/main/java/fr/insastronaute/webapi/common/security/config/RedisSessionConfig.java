package fr.insastronaute.webapi.common.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

import java.time.Duration;

@Configuration
@EnableRedisIndexedHttpSession
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis", matchIfMissing = true)
public class RedisSessionConfig {

    // Le depot indexe permet de retrouver toutes les sessions d'un sujet OIDC et de
    // les supprimer quand son role STUDENT est retire. Un jeton deja emis ne se modifie
    // pas : l'invalidation force sa recreation a la prochaine connexion.

    /**
     * Publie le serialiseur de cookie que Spring Session fabriquait en interne.
     *
     * <p>Spring Session en cree un lui-meme quand aucun bean n'existe, et le configure
     * depuis {@code ServletContext.getSessionCookieConfig()} -- c'est-a-dire depuis les
     * proprietes {@code server.servlet.session.cookie.*}. Il fonctionne, mais il n'est pas
     * accessible : rien d'autre ne peut reecrire le cookie avec les memes attributs.</p>
     *
     * <p>C'est ce qui empechait le glissement du cookie de s'installer. En production, le
     * demarrage l'annoncait : « Glissement du cookie de session inactif ». Le filtre etait
     * la, sa dependance n'existait pas, et le plafond de vingt-quatre heures tombait donc
     * sur des gens qui n'avaient pas cesse de naviguer.</p>
     *
     * <p>Le bean lit la meme source que le serialiseur interne, champ pour champ : publier
     * n'est pas reconfigurer. Ce qui n'est pas lu ici garde le defaut de
     * {@link DefaultCookieSerializer} -- le meme que celui qu'on remplace.</p>
     */
    @Bean
    CookieSerializer cookieSerializer(ServletContext servletContext) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        SessionCookieConfig cookieConfig = servletContext.getSessionCookieConfig();
        if (cookieConfig.getName() != null) {
            serializer.setCookieName(cookieConfig.getName());
        }
        if (cookieConfig.getDomain() != null) {
            serializer.setDomainName(cookieConfig.getDomain());
        }
        if (cookieConfig.getPath() != null) {
            serializer.setCookiePath(cookieConfig.getPath());
        }
        if (cookieConfig.getMaxAge() != -1) {
            serializer.setCookieMaxAge(cookieConfig.getMaxAge());
        }
        return serializer;
    }

    @Bean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionRepositoryCustomizer(
            @Value("${server.servlet.session.timeout:8h}") Duration sessionTimeout,
            @Value("${spring.session.redis.namespace:insastronaute:sessions}") String redisNamespace
    ) {
        return repository -> {
            repository.setDefaultMaxInactiveInterval(sessionTimeout);
            repository.setRedisKeyNamespace(redisNamespace);
            RedisSessionMapper mapper = new RedisSessionMapper();
            repository.setRedisSessionMapper((sessionId, entries) -> {
                // Une ecriture Redis interrompue peut laisser le hash de session sans ses
                // metadonnees. Spring Session leve alors une IllegalStateException a chaque
                // lecture ; le traiter comme absent laisse le navigateur se reconnecter.
                if (!(entries.get("creationTime") instanceof Long)
                        || !(entries.get("lastAccessedTime") instanceof Long)
                        || !(entries.get("maxInactiveInterval") instanceof Integer)) {
                    return null;
                }
                return mapper.apply(sessionId, entries);
            });
        };
    }
}
