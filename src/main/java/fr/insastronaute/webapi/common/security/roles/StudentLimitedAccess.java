package fr.insastronaute.webapi.common.security.roles;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

public final class StudentLimitedAccess {

    public static final String ROLE = "STUDENT_LIMITED";

    public static boolean isLimited(Collection<? extends GrantedAuthority> authorities) {
        return authorities != null && authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(value -> value.toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""))
                .anyMatch(ROLE::equals);
    }

    public static void requireAllowed(Collection<? extends GrantedAuthority> authorities) {
        if (isLimited(authorities)) {
            throw new StudentLimitedActionForbiddenException();
        }
    }

    private StudentLimitedAccess() {
    }
}
