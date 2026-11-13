package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryGrantRepository;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibraryAccessTest {

    private final MediaLibraryGrantRepository grants = mock(MediaLibraryGrantRepository.class);
    private final MediaLibraryAccess access = new MediaLibraryAccess(grants);

    @Test
    void anAdministratorNeedsNoGrant() {
        assertThat(access.canUse("admin-sub", Set.of("ROLE_ADMIN"))).isTrue();
        verify(grants, never()).existsByUser_KeycloakSub("admin-sub");
    }

    @Test
    void aDesignatedStaffMemberEnters() {
        when(grants.existsByUser_KeycloakSub("staff-sub")).thenReturn(true);

        assertThat(access.canUse("staff-sub", Set.of("ROLE_RESPOS_EVENT"))).isTrue();
    }

    @Test
    void staffWithoutAGrantStaysOut() {
        when(grants.existsByUser_KeycloakSub("staff-sub")).thenReturn(false);

        assertThat(access.canUse("staff-sub", Set.of("ROLE_RESPOS_DEFIS"))).isFalse();
        assertThatThrownBy(() -> access.require("staff-sub", Set.of("ROLE_RESPOS_DEFIS")))
                .isInstanceOf(ForbiddenException.class);
    }

    /** Une ligne restee en base apres la perte du role de staff ne rouvre rien. */
    @Test
    void aGrantWithoutAStaffRoleOpensNothing() {
        when(grants.existsByUser_KeycloakSub("student-sub")).thenReturn(true);

        assertThat(access.canUse("student-sub", Set.of("ROLE_STUDENT"))).isFalse();
    }
}
