package fr.insastronaute.webapi.common.security.roles;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentLimitedAccessTest {

    @Test
    void recognizesPrefixedStudentLimitedRole() {
        assertThat(StudentLimitedAccess.isLimited(List.of(authority("ROLE_STUDENT_LIMITED")))).isTrue();
    }

    @Test
    void recognizesUnprefixedStudentLimitedRole() {
        assertThat(StudentLimitedAccess.isLimited(List.of(authority("STUDENT_LIMITED")))).isTrue();
    }

    @Test
    void recognizesStudentLimitedRoleAlongsideOrdinaryRoles() {
        assertThat(StudentLimitedAccess.isLimited(List.of(
                authority("ROLE_STUDENT"), authority("ROLE_STUDENT_LIMITED")
        ))).isTrue();
    }

    @Test
    void doesNotLimitOrdinaryStudentRole() {
        assertThat(StudentLimitedAccess.isLimited(List.of(authority("ROLE_STUDENT")))).isFalse();
    }

    @Test
    void rejectsActionsForStudentLimitedRole() {
        assertThatThrownBy(() -> StudentLimitedAccess.requireAllowed(
                List.of(authority("ROLE_STUDENT_LIMITED"))))
                .isInstanceOf(StudentLimitedActionForbiddenException.class);
    }

    private static GrantedAuthority authority(String value) {
        return new SimpleGrantedAuthority(value);
    }
}
