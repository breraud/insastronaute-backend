package fr.insastronaute.webapi.common.security.session;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.web.http.CookieSerializer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Le cookie de session portait un {@code Expires} date a la connexion, que rien ne
 * repoussait : le plafond tombait meme sur quelqu'un qui n'avait pas cesse de naviguer, et
 * lui refaisait payer toute la chaine d'authentification.
 */
class SlidingSessionCookieFilterTest {

    private final CookieSerializer cookieSerializer = mock(CookieSerializer.class);
    private final Duration maxAge = Duration.ofHours(8);

    private final SlidingSessionCookieFilter filter =
            new SlidingSessionCookieFilter(cookieSerializer, maxAge);

    private MockHttpSession sessionIssuedAgo(Duration elapsed) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SlidingSessionCookieFilter.ISSUED_AT_ATTRIBUTE,
                System.currentTimeMillis() - elapsed.toMillis());
        return session;
    }

    private void run(MockHttpSession session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        if (session != null) {
            request.setSession(session);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void aCookiePastHalfItsLifeIsWrittenAgain() throws Exception {
        MockHttpSession session = sessionIssuedAgo(Duration.ofHours(5));

        run(session);

        verify(cookieSerializer, times(1)).writeCookieValue(any());
        assertThat(session.getAttribute(SlidingSessionCookieFilter.ISSUED_AT_ATTRIBUTE))
                .isNotNull();
    }

    /**
     * Reecrire a chaque reponse couterait un en-tete par requete pour ne rien changer.
     */
    @Test
    void aFreshCookieIsLeftAlone() throws Exception {
        run(sessionIssuedAgo(Duration.ofHours(1)));

        verify(cookieSerializer, never()).writeCookieValue(any());
    }

    @Test
    void aRequestWithoutASessionWritesNothing() throws Exception {
        run(null);

        verify(cookieSerializer, never()).writeCookieValue(any());
    }

    /**
     * Sans plafond configure, le cookie meurt avec le navigateur : il n'y a rien a faire
     * glisser, et le filtre doit s'effacer completement.
     */
    @Test
    void withoutAConfiguredMaxAgeTheFilterStandsAside() throws Exception {
        SlidingSessionCookieFilter inert = new SlidingSessionCookieFilter(cookieSerializer, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setSession(sessionIssuedAgo(Duration.ofHours(7)));

        inert.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(cookieSerializer, never()).writeCookieValue(any());
    }

    /**
     * Une session ouverte avant la mise en service n'a pas d'horodatage. Son cookie a ete
     * pose a la creation de la session : partir de maintenant lui accorderait un sursis
     * qu'elle n'a pas, et laisserait le plafond tomber sans prevenir.
     */
    @Test
    void aSessionThatPredatesTheFilterCountsFromItsCreation() throws Exception {
        long sixHoursAgo = System.currentTimeMillis() - Duration.ofHours(6).toMillis();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public long getCreationTime() {
                return sixHoursAgo;
            }
        };

        run(session);

        verify(cookieSerializer, times(1)).writeCookieValue(any());
    }

    /**
     * Spring Session est hors service en test, et le serialiseur avec lui. Le filtre doit
     * traverser sans rien tenter plutot que d'exiger un bean absent - c'est ce que la
     * configuration lui passe alors.
     */
    @Test
    void withoutASerializerTheFilterStandsAside() throws Exception {
        SlidingSessionCookieFilter inert = new SlidingSessionCookieFilter(null, maxAge);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setSession(sessionIssuedAgo(Duration.ofHours(7)));

        inert.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(cookieSerializer, never()).writeCookieValue(any());
    }
}
