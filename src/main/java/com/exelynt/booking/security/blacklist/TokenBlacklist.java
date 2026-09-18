package com.exelynt.booking.security.blacklist;

import java.time.Instant;

/**
 * Store of access-token ids revoked before their natural expiry.
 *
 * <p>The application only depends on this interface, so the in-memory
 * implementation can be swapped for a Redis-backed one without touching the
 * filter or the auth service.</p>
 */
public interface TokenBlacklist {

    /** Blocks {@code jti} until {@code expiresAt}; entries past that point may be dropped. */
    void blacklist(String jti, Instant expiresAt);

    boolean isBlacklisted(String jti);
}
