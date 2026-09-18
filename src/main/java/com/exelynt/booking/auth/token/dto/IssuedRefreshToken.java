package com.exelynt.booking.auth.token.dto;

import java.time.LocalDateTime;

/**
 * A freshly issued refresh token. {@code value} is the opaque secret handed to
 * the client; it is never persisted, only a hash of it is.
 */
public record IssuedRefreshToken(String value, LocalDateTime expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken{value='[PROTECTED]', expiresAt=" + expiresAt + "}";
    }
}
