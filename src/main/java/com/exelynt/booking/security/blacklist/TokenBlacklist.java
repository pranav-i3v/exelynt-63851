package com.exelynt.booking.security.blacklist;

import java.time.Instant;

/**
 * Store of access-token ids revoked before their natural expiry.
 *
 * <p>Backed by Redis, so a logout is honoured by every instance and not only
 * by the one that served it. The interface remains so the filter and the auth
 * service depend on the behaviour rather than on Redis itself.</p>
 */
public interface TokenBlacklist {

    /** Blocks {@code jti} until {@code expiresAt}; entries past that point may be dropped. */
    void blacklist(String jti, Instant expiresAt);

    boolean isBlacklisted(String jti);
}
