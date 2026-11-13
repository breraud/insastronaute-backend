package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La duree d'une session suit ce que le compte permet.
 *
 * <p>Un compte de staff ouvre des portes qu'un compte de joueur n'ouvre pas : la duree
 * pendant laquelle un telephone pose sur une table reste ce compte-la doit suivre ce qu'il
 * permet, pas le confort de son porteur. A l'inverse, reconnecter deux mille etudiants
 * chaque matin, c'est deux mille passages par Keycloak au moment ou tout le monde
 * arrive.</p>
 */
class OAuth2LoginSessionTimeoutTest {

    private final AppSecurityProperties properties = new AppSecurityProperties();

    private OAuth2LoginSuccessHandler handler() {
        properties.setFrontendBaseUrl("https://app.insastronaute.fr");
        properties.setStaffSessionTimeout(Duration.ofHours(8));
        properties.setPlayerSessionTimeout(Duration.ofHours(24));
        return new OAuth2LoginSuccessHandler(properties, Set.of("ADMIN", "RESPOS_EVENT"));
    }

    private MockHttpSession sessionAfterLoginAs(String... roles) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        handler().onAuthenticationSuccess(
                request,
                new MockHttpServletResponse(),
                new TestingAuthenticationToken("compte", "n/a",
                        AuthorityUtils.createAuthorityList(roles)));
        return session;
    }

    @Test
    void aStaffSessionExpiresAfterEightHours() throws Exception {
        assertThat(sessionAfterLoginAs("ROLE_ADMIN").getMaxInactiveInterval())
                .isEqualTo((int) Duration.ofHours(8).toSeconds());
    }

    @Test
    void aPlayerSessionLastsTheDay() throws Exception {
        assertThat(sessionAfterLoginAs("ROLE_STUDENT").getMaxInactiveInterval())
                .isEqualTo((int) Duration.ofHours(24).toSeconds());
    }

    /**
     * Un seul role de staff suffit a raccourcir : quelqu'un qui est a la fois joueur et
     * respo garde le compte le plus expose des deux, pas le plus confortable.
     */
    @Test
    void beingAlsoAPlayerDoesNotLengthenAStaffSession() throws Exception {
        assertThat(sessionAfterLoginAs("ROLE_STUDENT", "ROLE_RESPOS_EVENT").getMaxInactiveInterval())
                .isEqualTo((int) Duration.ofHours(8).toSeconds());
    }

    /**
     * Un role de staff que la liste ne nomme pas ne raccourcit rien. Le test le dit pour
     * qu'ajouter un role sans l'ajouter a la liste se remarque ici, et non le jour ou l'on
     * s'apercoit qu'un compte a privileges dormait vingt-quatre heures.
     */
    @Test
    void onlyTheNamedRolesShortenTheSession() throws Exception {
        assertThat(sessionAfterLoginAs("ROLE_MODERATOR").getMaxInactiveInterval())
                .isEqualTo((int) Duration.ofHours(24).toSeconds());
    }
}
