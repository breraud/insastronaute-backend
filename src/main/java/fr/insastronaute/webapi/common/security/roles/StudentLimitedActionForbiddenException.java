package fr.insastronaute.webapi.common.security.roles;

public class StudentLimitedActionForbiddenException extends RuntimeException {

    public StudentLimitedActionForbiddenException() {
        super("Cette action n'est pas autorisee pour ce compte.");
    }
}
