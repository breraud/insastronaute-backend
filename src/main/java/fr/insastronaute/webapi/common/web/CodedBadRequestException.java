package fr.insastronaute.webapi.common.web;

public class CodedBadRequestException extends BadRequestException {

    private final String code;

    public CodedBadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
