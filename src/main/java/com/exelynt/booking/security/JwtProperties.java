package com.exelynt.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings.
 *
 * <p>Tokens are signed with RS256. The RSA key pair is held by AWS Secrets
 * Manager and nowhere else: there is no configuration property, environment
 * variable or file from which the application will accept key material, and it
 * never generates a key pair of its own.</p>
 *
 * <p>Everything is validated in the canonical constructor, so a misconfigured
 * secret fails the application at startup instead of at the first login.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        long accessExpiryMinutes,
        long refreshExpiryDays,
        String issuer,
        Aws aws) {

    /** RSA keys below 2048 bits are not accepted. */
    public static final int MIN_KEY_SIZE_BITS = 2048;

    /** Location of the AWS Secrets Manager secret holding the key pair, and how often to re-read it. */
    public record Aws(String secretId, String region, long refreshMinutes) {
    }

    public JwtProperties {
        aws = aws == null ? new Aws(null, null, 0L) : aws;

        if (accessExpiryMinutes <= 0) {
            throw new IllegalStateException("JWT_ACCESS_EXPIRY_MINUTES must be greater than zero");
        }
        if (refreshExpiryDays <= 0) {
            throw new IllegalStateException("JWT_REFRESH_EXPIRY_DAYS must be greater than zero");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer must be configured");
        }
        requireSecretId(aws.secretId());
    }

    public long accessExpirySeconds() {
        return accessExpiryMinutes * 60L;
    }

    /**
     * Spring's binder leaves an unresolvable {@code ${VAR}} in place rather than
     * failing, so an unset variable would otherwise look like a literal value.
     */
    private static void requireSecretId(String secretId) {
        if (secretId == null || secretId.isBlank()) {
            throw new IllegalStateException("app.jwt.aws.secret-id must name the AWS Secrets Manager secret "
                    + "holding the RSA signing key; set JWT_SECRET_ID (see .env.example)");
        }
        if (secretId.startsWith("${") && secretId.endsWith("}")) {
            throw new IllegalStateException("JWT_SECRET_ID is not set: the placeholder " + secretId
                    + " could not be resolved. Export it as an environment variable (see .env.example)");
        }
    }
}
