package com.exelynt.booking.common.exception;

/** Maps to HTTP 409: the request is valid but conflicts with the current state. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
