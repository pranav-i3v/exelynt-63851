package com.exelynt.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings.
 *
 * <p>Tokens are signed with RS256, so the application holds an RSA key pair
 * rather than a shared secret. Where that key pair comes from is decided by
 * {@link KeySource}; in production it is AWS Secrets Manager.</p>
 *
 * <p>Everything is validated in the canonical constructor, so a misconfigured
 * key source fails the application at startup instead of at the first login.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        KeySource keySource,
        long accessExpiryMinutes,
        long refreshExpiryDays,
        String issuer,
        Pem pem,
        Aws aws) {

    /** RSA keys below 2048 bits are not accepted. */
    public static final int MIN_KEY_SIZE_BITS = 2048;

    /** Where the RSA key pair is loaded from. */
    public enum KeySource {
        /** Production: the key pair is read from AWS Secrets Manager. */
        AWS_SECRETS_MANAGER,
        /** Local development: PEM-encoded keys supplied through configuration. */
        PEM,
        /** Tests only: an ephemeral key pair generated at startup. */
        GENERATED
    }

    /** PEM-encoded key material supplied directly (PKCS#8 private key, X.509 public key). */
    public record Pem(String privateKey, String publicKey, String keyId) {
    }

    /** Location of the secret holding the key pair, plus how often to re-read it. */
    public record Aws(String secretId, String region, long refreshMinutes) {
    }

    public JwtProperties {
        keySource = keySource == null ? KeySource.AWS_SECRETS_MANAGER : keySource;
        pem = pem == null ? new Pem(null, null, null) : pem;
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

        switch (keySource) {
            case AWS_SECRETS_MANAGER -> requireConfigured(aws.secretId(), "JWT_SECRET_ID",
                    "app.jwt.aws.secret-id must name the AWS Secrets Manager secret holding the RSA key pair");
            case PEM -> requireConfigured(pem.privateKey(), "JWT_PRIVATE_KEY",
                    "app.jwt.pem.private-key must contain a PKCS#8 PEM private key");
            case GENERATED -> {
                // Nothing to configure: the key pair is created in memory at startup.
            }
        }
    }

    public long accessExpirySeconds() {
        return accessExpiryMinutes * 60L;
    }

    /**
     * Spring's binder leaves an unresolvable {@code ${VAR}} in place rather than
     * failing, so an unset variable would otherwise look like a literal value.
     */
    private static void requireConfigured(String value, String variableName, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message + "; set " + variableName + " (see .env.example)");
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            throw new IllegalStateException(variableName + " is not set: the placeholder " + value
                    + " could not be resolved. Export it as an environment variable (see .env.example)");
        }
    }
}
