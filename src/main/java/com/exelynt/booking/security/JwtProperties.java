package com.exelynt.booking.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings, bound from environment variables. Every value is validated in
 * the canonical constructor, so a missing or weak {@code JWT_SECRET} fails the
 * application at startup instead of at the first login.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessExpiryMinutes, long refreshExpiryDays, String issuer) {

    /** HS256 requires a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be provided; set it as an environment variable (see .env.example)");
        }
        // Spring's binder leaves an unresolvable ${VAR} in place rather than failing,
        // so an unset variable would otherwise be mistaken for a (weak) literal secret.
        if (secret.startsWith("${") && secret.endsWith("}")) {
            throw new IllegalStateException("JWT_SECRET is not set: the placeholder " + secret
                    + " could not be resolved. Export it as an environment variable (see .env.example)");
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
                    + " bytes long for HS256, but is " + length + " bytes");
        }
        if (accessExpiryMinutes <= 0) {
            throw new IllegalStateException("JWT_ACCESS_EXPIRY_MINUTES must be greater than zero");
        }
        if (refreshExpiryDays <= 0) {
            throw new IllegalStateException("JWT_REFRESH_EXPIRY_DAYS must be greater than zero");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer must be configured");
        }
    }

    public long accessExpirySeconds() {
        return accessExpiryMinutes * 60L;
    }
}
