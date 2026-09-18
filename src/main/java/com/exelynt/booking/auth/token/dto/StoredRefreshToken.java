package com.exelynt.booking.auth.token.dto;

import java.time.LocalDateTime;

/**
 * Storage-agnostic view of a refresh token that was looked up.
 *
 * <p>Deliberately carries no JPA entity, so the store can be backed by a
 * relational table, Redis or anything else without the auth service noticing.
 * It carries {@code userId} rather than a {@code User} for the same reason.</p>
 */
public record StoredRefreshToken(
        String tokenHash,
        Long userId,
        String username,
        LocalDateTime expiresAt,
        boolean revoked) {

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        return !revoked && !isExpired();
    }

    /**
     * A revoked token presented again is the signal that matters: rotation
     * already consumed it, so whoever is holding this copy should not have it.
     */
    public boolean isReplayOfConsumedToken() {
        return revoked && !isExpired();
    }

    @Override
    public String toString() {
        return "StoredRefreshToken{userId=" + userId + ", expiresAt=" + expiresAt + ", revoked=" + revoked + "}";
    }
}
