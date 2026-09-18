package com.exelynt.booking.security;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Access-token blacklist shared by every instance, backed by Redis.
 *
 * <p>The in-memory implementation is per-process: a logout handled by one
 * instance leaves the token usable on the others until it expires. Once more
 * than one replica runs, that is a hole rather than an inefficiency, which is
 * what this exists to close.</p>
 *
 * <p>Each revoked {@code jti} is a key with a TTL matching what is left of the
 * token's life, so Redis expires the entry exactly when the token would have
 * stopped being accepted anyway; nothing has to be swept.</p>
 *
 * <p>Failures are not swallowed. If Redis cannot be reached, the exception
 * propagates: a logout that reports success without revoking anything, or a
 * request admitted because the revocation list could not be read, are both
 * worse than a visible error.</p>
 */
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklist.class);
    private static final String KEY_PREFIX = "booking:blacklist:jti:";
    private static final String VALUE = "revoked";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("token_blacklist_backend backend=redis");
    }

    @Override
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            // Already expired: the token is refused on its own expiry claim.
            return;
        }
        redisTemplate.opsForValue().set(key(jti), VALUE, ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    private static String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
