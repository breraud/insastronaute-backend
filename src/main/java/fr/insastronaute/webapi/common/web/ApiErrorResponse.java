package fr.insastronaute.webapi.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {
    public ApiErrorResponse(
            OffsetDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            List<FieldViolation> fieldErrors
    ) {
        this(timestamp, status, error, message, path, fieldErrors, null);
    }

    public static ApiErrorResponse withoutFieldErrors(
            int status,
            String error,
            String message,
            String path
    ) {
        return new ApiErrorResponse(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow(), status, error, message, path, List.of());
    }

    public record FieldViolation(
            String field,
            String message
    ) {
    }
}
