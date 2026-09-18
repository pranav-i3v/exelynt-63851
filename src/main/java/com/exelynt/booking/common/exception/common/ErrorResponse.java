package com.exelynt.booking.common.exception.common;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The single error shape returned by every failing endpoint, whether the failure
 * is raised by a servlet filter, by Spring Security or by a controller.
 */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(int status,
                                   String error,
                                   String message,
                                   String path,
                                   String correlationId,
                                   List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                status,
                error,
                message,
                path,
                correlationId,
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors));
    }

    public static ErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return of(status, error, message, path, correlationId, List.of());
    }
}
