package fr.insastronaute.webapi.auth.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakDepartmentGroupSynchronizationServiceTest {

    private final KeycloakGroupMembershipGateway gateway = mock(KeycloakGroupMembershipGateway.class);
    private final KeycloakDepartmentGroupSynchronizationService service =
            new KeycloakDepartmentGroupSynchronizationService(gateway, true);
    private final OidcUser user = mock(OidcUser.class);

    @Test
    void synchronizesManagedGroupsCaseInsensitivelyAndPreservesUnrelatedGroups() {
        when(user.getSubject()).thenReturn("user-123");
        when(user.getClaimAsStringList("department")).thenReturn(List.of(" iti ", "MECA", "unknown"));
        when(gateway.listRealmGroups()).thenReturn(List.of(
                group("cfi-id", "CFI"), group("iti-id", "ITI"), group("meca-id", "MECA"),
                group("mrie-id", "MRIE"), group("other-id", "Association")
        ));
        when(gateway.listUserGroups("user-123")).thenReturn(List.of(
                group("cfi-id", "CFI"), group("iti-id", "ITI"), group("other-id", "Association")
        ));

        service.synchronize(user);

        verify(gateway).joinGroup("user-123", "meca-id");
        verify(gateway).leaveGroup("user-123", "cfi-id");
        verify(gateway, never()).leaveGroup("user-123", "other-id");
        verify(gateway, never()).joinGroup("user-123", "iti-id");
    }

    @Test
    void removesAllManagedGroupsWhenDepartmentClaimIsPresentAndEmpty() {
        when(user.getSubject()).thenReturn("user-123");
        when(user.getClaimAsStringList("department")).thenReturn(List.of());
        when(gateway.listRealmGroups()).thenReturn(List.of(group("iti-id", "ITI")));
        when(gateway.listUserGroups("user-123")).thenReturn(List.of(group("iti-id", "ITI")));

        service.synchronize(user);

        verify(gateway).leaveGroup("user-123", "iti-id");
    }

    @Test
    void doesNothingWhenDepartmentClaimIsAbsent() {
        when(user.getClaimAsStringList("department")).thenReturn(null);

        service.synchronize(user);

        verify(gateway, never()).listRealmGroups();
    }

    @Test
    void doesNothingWhenSynchronizationIsDisabled() {
        KeycloakDepartmentGroupSynchronizationService disabledService =
                new KeycloakDepartmentGroupSynchronizationService(gateway, false);

        disabledService.synchronize(user);

        verify(gateway, never()).listRealmGroups();
    }

    @Test
    void failsOpenWhenTheGatewayFails() {
        when(user.getSubject()).thenReturn("user-123");
        when(user.getClaimAsStringList("department")).thenReturn(List.of("ITI"));
        when(gateway.listRealmGroups()).thenThrow(new IllegalStateException("Keycloak unavailable"));

        service.synchronize(user);
    }

    private static KeycloakGroupMembershipGateway.Group group(String id, String name) {
        return new KeycloakGroupMembershipGateway.Group(id, name);
    }
}
