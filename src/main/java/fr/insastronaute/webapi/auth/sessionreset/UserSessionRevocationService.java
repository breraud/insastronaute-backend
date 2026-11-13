package fr.insastronaute.webapi.auth.sessionreset;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Invalide toutes les sessions applicatives d'un compte Keycloak. */
@Service
public class UserSessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    public UserSessionRevocationService(
            ObjectProvider<RedisIndexedSessionRepository> sessionsProvider
    ) {
        this.sessions = sessionsProvider.getIfAvailable();
    }

    UserSessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * Supprime toutes les sessions indexees sous le sujet OIDC.
     *
     * <p>Le depot peut manquer dans les profils de test et de generation OpenAPI, qui
     * desactivent Spring Session. En production, son absence est une erreur : continuer
     * laisserait precisement vivre les autorites que l'appel promet de retirer.</p>
     */
    public int revokeAll(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new IllegalArgumentException("Le principal a revoquer est requis.");
        }
        if (sessions == null) {
            throw new IllegalStateException("Le depot de sessions indexees n'est pas disponible.");
        }

        Map<String, ? extends Session> indexedSessions = sessions.findByPrincipalName(principalName);
        indexedSessions.keySet().forEach(sessions::deleteById);
        return indexedSessions.size();
    }
}
