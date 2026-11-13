package fr.insastronaute.webapi.common.web;

public class CodedConflictException extends RuntimeException {
    private final String code;

    public CodedConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
