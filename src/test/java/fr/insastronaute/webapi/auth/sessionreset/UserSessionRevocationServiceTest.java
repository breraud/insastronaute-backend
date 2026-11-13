package fr.insastronaute.webapi.auth.sessionreset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionRevocationServiceTest {

    @Mock
    private FindByIndexNameSessionRepository<Session> sessions;

    @Test
    void revokesEveryApplicationSessionIndexedForTheUser() {
        when(sessions.findByPrincipalName("keycloak-sub"))
                .thenReturn(Map.of(
                        "session-1", mock(Session.class),
                        "session-2", mock(Session.class)
                ));
        UserSessionRevocationService service = new UserSessionRevocationService(sessions);

        int revoked = service.revokeAll("keycloak-sub");

        assertThat(revoked).isEqualTo(2);
        verify(sessions).deleteById("session-1");
        verify(sessions).deleteById("session-2");
    }
}
