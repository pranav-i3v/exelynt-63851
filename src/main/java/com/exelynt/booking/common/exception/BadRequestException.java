package com.exelynt.booking.common.exception;

/** Maps to HTTP 400 for validation failures detected outside Bean Validation. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
