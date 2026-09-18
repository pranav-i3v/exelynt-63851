package com.exelynt.booking.security.blacklist;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node {@link TokenBlacklist}. Entries are purged lazily once their
 * token would have expired anyway, so the map stays bounded by the number of
 * logouts within one access-token lifetime.
 *
 * <p>The state lives in this process only, so it is correct for a single
 * instance and for tests. Running more than one replica needs the Redis
 * implementation, or a logout on one instance will not be honoured by the
 * others. {@code TokenBlacklistConfiguration} picks between them.</p>
 */
public class InMemoryTokenBlacklist implements TokenBlacklist {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        purgeExpired();
        revokedUntil.put(jti, expiresAt);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Instant expiry = revokedUntil.get(jti);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            revokedUntil.remove(jti);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedUntil.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
