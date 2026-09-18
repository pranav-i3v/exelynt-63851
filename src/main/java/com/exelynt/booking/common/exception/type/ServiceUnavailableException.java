package com.exelynt.booking.common.exception.type;

/**
 * Maps to HTTP 503: a dependency the request needs is not answering.
 *
 * <p>Used where failing is the only safe option — a revocation check that cannot
 * reach Redis, for instance. Admitting the request instead would mean honouring
 * a token that may well have been revoked.</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
