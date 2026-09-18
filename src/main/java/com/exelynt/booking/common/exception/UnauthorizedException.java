package com.exelynt.booking.common.exception;

/** Maps to HTTP 401: credentials or tokens are missing, invalid or no longer usable. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
