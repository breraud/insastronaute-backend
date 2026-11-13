package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryGrant;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryGrantRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibraryGrantServiceTest {

    private final MediaLibraryGrantRepository grants = mock(MediaLibraryGrantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RealmRoleDirectory roles = mock(RealmRoleDirectory.class);
    private final AdminActionJournal journal = mock(AdminActionJournal.class);
    private final MediaLibraryGrantService service = new MediaLibraryGrantService(grants, users, roles, journal);

    @Test
    void grantingAStaffMemberWritesTheRowAndTheJournal() {
        AppUser staff = user("staff-sub");
        AppUser admin = user("admin-sub");
        when(users.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(users.findByKeycloakSub("admin-sub")).thenReturn(Optional.of(admin));
        when(roles.isKnown()).thenReturn(true);
        when(roles.isStaff("staff-sub")).thenReturn(true);

        service.grant(staff.getId(), "admin-sub");

        verify(grants).save(any(MediaLibraryGrant.class));
        verify(journal).record("admin-sub", staff.getId(), AdminActionType.MEDIA_LIBRARY_ACCESS_GRANTED, Map.of());
    }

    @Test
    void grantingTwiceWritesNothingTheSecondTime() {
        AppUser staff = user("staff-sub");
        when(users.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(grants.existsById(staff.getId())).thenReturn(true);

        service.grant(staff.getId(), "admin-sub");

        verify(grants, never()).save(any());
        verify(journal, never()).record(any(), any(), any(), anyMap());
    }

    @Test
    void aStudentCannotBeDesignated() {
        AppUser student = user("student-sub");
        when(users.findById(student.getId())).thenReturn(Optional.of(student));
        when(roles.isKnown()).thenReturn(true);
        when(roles.isStaff("student-sub")).thenReturn(false);

        assertThatThrownBy(() -> service.grant(student.getId(), "admin-sub"))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("MEDIA_LIBRARY_GRANT_NOT_STAFF"));
    }

    @Test
    void anUnknownDirectoryRefusesRatherThanGuesses() {
        AppUser staff = user("staff-sub");
        when(users.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(roles.isKnown()).thenReturn(false);

        assertThatThrownBy(() -> service.grant(staff.getId(), "admin-sub"))
                .isInstanceOf(RoleDirectoryUnavailableException.class);
    }

    @Test
    void revokingWritesTheJournalOnlyWhenSomethingChanged() {
        UUID holder = UUID.randomUUID();
        when(grants.existsById(holder)).thenReturn(true, false);

        service.revoke(holder, "admin-sub");
        service.revoke(holder, "admin-sub");

        verify(grants).deleteById(holder);
        verify(journal).record(eq("admin-sub"), eq(holder), eq(AdminActionType.MEDIA_LIBRARY_ACCESS_REVOKED), anyMap());
    }

    private static AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(subject);
        user.setDisplayName(subject);
        return user;
    }
}
