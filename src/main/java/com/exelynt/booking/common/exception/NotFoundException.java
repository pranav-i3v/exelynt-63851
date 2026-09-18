package com.exelynt.booking.common.exception;

/** Maps to HTTP 404. Also used to hide resources the caller may not see. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " " + id + " was not found");
    }
}
