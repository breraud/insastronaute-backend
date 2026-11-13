package fr.insastronaute.webapi.common.web;

import java.util.List;

public class BadRequestException extends RuntimeException {

    private final List<ApiErrorResponse.FieldViolation> fieldErrors;

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, String field, String fieldMessage) {
        this(message, List.of(new ApiErrorResponse.FieldViolation(field, fieldMessage)));
    }

    public BadRequestException(String message, List<ApiErrorResponse.FieldViolation> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public List<ApiErrorResponse.FieldViolation> getFieldErrors() {
        return fieldErrors;
    }
}
