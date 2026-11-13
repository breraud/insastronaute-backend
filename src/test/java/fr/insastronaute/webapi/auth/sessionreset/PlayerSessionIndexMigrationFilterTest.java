package fr.insastronaute.webapi.auth.sessionreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerSessionIndexMigrationFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlayerSessionIndexMigrationFilter filter = new PlayerSessionIndexMigrationFilter(
            userRepository,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            Set.of("ADMIN", "RESPOS_DEFIS")
    );

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRejectedPlayerSessionCreatedBeforeTheRedisIndexIsClosedBeforeAuthorization() throws Exception {
        String subject = "keycloak-sub";
        AppUser user = new AppUser();
        user.setOnboardingStatus(OnboardingStatus.REJECTED);
        when(userRepository.findByKeycloakSub(subject)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                subject,
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_STUDENT")
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/challenges");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ONBOARDING_SESSION_REVOKED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void anExistingPlayerSessionIsStampedSoTheIndexedRepositoryWillSaveIt() throws Exception {
        String subject = "keycloak-sub";
        AppUser user = new AppUser();
        user.setOnboardingStatus(OnboardingStatus.VALIDATED);
        when(userRepository.findByKeycloakSub(subject)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                subject,
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_STUDENT")
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/challenges");
        TrackingSession session = new TrackingSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
        session.writtenAttributes.clear();
        request.setSession(session);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.getAttribute(PlayerSessionIndexMigrationFilter.SESSION_ATTRIBUTE)).isEqualTo(true);
        assertThat(session.writtenAttributes)
                .contains(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void anAlreadyIndexedPlayerSessionDoesNotHitTheDatabaseAgain() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "keycloak-sub",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_STUDENT")
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/challenges");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PlayerSessionIndexMigrationFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
        request.setSession(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(userRepository);
    }

    private static final class TrackingSession extends MockHttpSession {
        private final Set<String> writtenAttributes = new HashSet<>();

        @Override
        public void setAttribute(String name, Object value) {
            super.setAttribute(name, value);
            writtenAttributes.add(name);
        }
    }
}
