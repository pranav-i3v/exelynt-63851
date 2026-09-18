package com.exelynt.booking.common.exception;

/** A single field-level validation failure inside an {@link ErrorResponse}. */
public record FieldErrorDetail(String field, String message) {
}
