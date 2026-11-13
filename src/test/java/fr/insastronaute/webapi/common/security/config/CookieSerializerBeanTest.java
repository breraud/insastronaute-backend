package fr.insastronaute.webapi.common.security.config;

import jakarta.servlet.SessionCookieConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.session.web.http.CookieSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le serialiseur de cookie, publie plutot que garde par Spring Session.
 *
 * <p>Spring Session en fabrique un en interne quand aucun bean n'existe, depuis
 * {@code ServletContext.getSessionCookieConfig()}. Il fonctionne, mais rien d'autre ne peut
 * s'en servir -- et c'est ce qui empechait le glissement du cookie de s'installer : en
 * production, le demarrage annoncait « Glissement du cookie de session inactif », le filtre
 * etait la, sa dependance n'existait pas.</p>
 *
 * <p>Publier n'est pas reconfigurer : le bean doit lire la meme source, champ pour champ.
 * Un ecart ici changerait les cookies d'authentification de tout le monde.</p>
 */
class CookieSerializerBeanTest {

    private CookieSerializer serializerFor(String name, String path, String domain, int maxAge) {
        MockServletContext servletContext = new MockServletContext();
        SessionCookieConfig config = servletContext.getSessionCookieConfig();
        config.setName(name);
        config.setPath(path);
        config.setDomain(domain);
        config.setMaxAge(maxAge);
        return new RedisSessionConfig().cookieSerializer(servletContext);
    }

    private String writtenCookie(CookieSerializer serializer) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "identifiant"));
        return response.getHeader("Set-Cookie");
    }

    @Test
    void theCookieCarriesWhatTheServletConfigurationDeclares() {
        String cookie = writtenCookie(serializerFor("SESSION", "/", null, 86400));

        assertThat(cookie).startsWith("SESSION=");
        assertThat(cookie).contains("Path=/");
        assertThat(cookie).contains("Max-Age=86400");
    }

    /**
     * Un domaine absent doit le rester : en poser un elargirait la portee du cookie
     * d'authentification a tous les sous-domaines, ce que la production ne fait pas.
     */
    @Test
    void noDomainIsAddedWhenNoneIsConfigured() {
        assertThat(writtenCookie(serializerFor("SESSION", "/", null, 86400)))
                .doesNotContain("Domain=");
    }

    @Test
    void aConfiguredDomainIsHonoured() {
        assertThat(writtenCookie(serializerFor("SESSION", "/", "insastronaute.fr", 86400)))
                .contains("Domain=insastronaute.fr");
    }

    /**
     * `-1` est la valeur que le conteneur donne quand aucune duree n'est configuree. La
     * recopier telle quelle poserait un cookie de session navigateur, la ou le defaut de
     * Spring Session est autre chose : on laisse alors le serialiseur decider.
     */
    @Test
    void anUnsetMaxAgeIsLeftToTheSerializer() {
        assertThat(writtenCookie(serializerFor("SESSION", "/", null, -1)))
                .doesNotContain("Max-Age=-1");
    }
}
