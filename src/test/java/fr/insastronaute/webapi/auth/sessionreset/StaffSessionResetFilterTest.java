package fr.insastronaute.webapi.auth.sessionreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ce que voit une session de staff apres une reattribution des roles.
 *
 * <p>Le compteur est commun et les sessions ne sont pas indexees par utilisateur : c'est
 * chaque session qui constate son propre retard, a sa requete suivante.</p>
 */
class StaffSessionResetFilterTest {

    private final StaffSessionResetService resetService = mock(StaffSessionResetService.class);

    private final StaffSessionResetFilter filter = new StaffSessionResetFilter(
            resetService, new ObjectMapper().registerModule(new JavaTimeModule()), Set.of("ADMIN", "RESPOS_EVENT"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse pass(String role, Object stamp, long currentEpoch) throws Exception {
        when(resetService.currentEpoch()).thenReturn(currentEpoch);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("compte", "n/a", AuthorityUtils.createAuthorityList("ROLE_" + role)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        MockHttpSession session = new MockHttpSession();
        if (stamp != null) {
            session.setAttribute(StaffSessionResetFilter.SESSION_ATTRIBUTE, stamp);
        }
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void aStaffSessionLeftBehindByAResetIsSentBackToKeycloak() throws Exception {
        MockHttpServletResponse response = pass("ADMIN", 4L, 5L);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ROLES_RESET");
    }

    @Test
    void aStaffSessionUpToDatePassesThrough() throws Exception {
        MockHttpServletResponse response = pass("ADMIN", 5L, 5L);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Une session ouverte avant la mise en service n'a jamais vu le compteur. Elle prend
     * celui du moment : sinon le premier deploiement deconnecterait tout le staff pour une
     * reinitialisation que personne n'a demandee.
     */
    @Test
    void aSessionThatPredatesTheCounterAdoptsItInsteadOfEnding() throws Exception {
        when(resetService.currentEpoch()).thenReturn(5L);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("compte", "n/a", AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(session.getAttribute(StaffSessionResetFilter.SESSION_ATTRIBUTE)).isEqualTo(5L);
    }

    /**
     * Un joueur traverse sans rien voir, meme tres en retard sur le compteur. Le faire
     * repasser par Keycloak enverrait toute la promotion sur le portail d'un seul coup,
     * pour des roles qui n'ont pas bouge.
     */
    @Test
    void aPlayerIsNeverDisturbedByAStaffReset() throws Exception {
        MockHttpServletResponse response = pass("STUDENT", 0L, 12L);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void anAnonymousVisitorIsNeverDisturbed() throws Exception {
        when(resetService.currentEpoch()).thenReturn(9L);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "cle", "anonymousUser", List.copyOf(AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
