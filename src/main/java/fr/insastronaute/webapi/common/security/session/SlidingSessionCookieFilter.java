package fr.insastronaute.webapi.common.security.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Fait glisser la duree de vie du cookie de session, comme celle de la session elle-meme.
 *
 * <p>Il y avait deux horloges, et une seule se rechargeait. Cote serveur, {@code
 * session.timeout} est une fenetre d'inactivite : chaque requete la repousse. Cote
 * navigateur, {@code cookie.max-age} pose un {@code Expires} date en dur au moment de la
 * connexion, et le cookie n'est reemis que lorsque l'identifiant de session change - donc
 * jamais, en usage normal.</p>
 *
 * <p>Le plafond dur gagnait toujours : meme en naviguant sans arret, le navigateur
 * supprimait le cookie huit heures apres la connexion. La session vivait encore dans Redis,
 * mais plus personne ne pouvait la presenter. Et un cookie mort ne coute pas une
 * reconnexion : il coute toute la chaine d'authentification, portail d'attente compris,
 * pour quelqu'un qui n'avait jamais cesse d'etre connecte.</p>
 *
 * <p>Le cookie est reecrit une fois passee la moitie de sa vie, pas a chaque reponse : un
 * en-tete {@code Set-Cookie} sur chaque requete couterait pour ne rien changer. Avec huit
 * heures, cela fait au plus une reemission toutes les quatre heures et par personne.</p>
 */
public class SlidingSessionCookieFilter extends OncePerRequestFilter {

    static final String ISSUED_AT_ATTRIBUTE = "insastronaute.sessionCookieIssuedAt";

    private final CookieSerializer cookieSerializer;
    private final Duration maxAge;

    public SlidingSessionCookieFilter(CookieSerializer cookieSerializer, Duration maxAge) {
        this.cookieSerializer = cookieSerializer;
        this.maxAge = maxAge;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Sans plafond configure, le cookie meurt avec le navigateur et il n'y a rien a
        // faire glisser.
        return cookieSerializer == null || maxAge == null || maxAge.isZero() || maxAge.isNegative();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Avant la chaine : une fois la reponse engagee, plus aucun en-tete ne peut etre
        // ajoute, et l'ecriture serait perdue sans bruit.
        reissueIfHalfSpent(request, response);
        filterChain.doFilter(request, response);
    }

    private void reissueIfHalfSpent(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Object stored = session.getAttribute(ISSUED_AT_ATTRIBUTE);

        // Les sessions ouvertes avant ce filtre n'ont pas d'horodatage. Leur cookie a ete
        // pose a la creation de la session : c'est la le point de depart, pas maintenant,
        // sinon on leur accorderait un sursis qu'elles n'ont pas.
        long issuedAt = stored instanceof Long value ? value : session.getCreationTime();

        if (now - issuedAt < maxAge.toMillis() / 2) {
            return;
        }

        cookieSerializer.writeCookieValue(
                new CookieSerializer.CookieValue(request, response, session.getId()));
        session.setAttribute(ISSUED_AT_ATTRIBUTE, now);
    }
}
