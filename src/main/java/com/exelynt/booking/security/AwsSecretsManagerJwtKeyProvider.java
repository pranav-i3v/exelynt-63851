package com.exelynt.booking.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the RS256 key pair from AWS Secrets Manager.
 *
 * <p>The secret may hold either a bare PKCS#8 PEM private key or a JSON document:</p>
 *
 * <pre>{@code
 * { "privateKey": "-----BEGIN PRIVATE KEY-----\n...",
 *   "publicKey":  "-----BEGIN PUBLIC KEY-----\n...",   // optional, derived when absent
 *   "keyId":      "booking-2026-09" }                  // optional, fingerprinted when absent
 * }</pre>
 *
 * <p>The key pair is fetched once at startup, so a missing or unreadable secret
 * fails the application immediately rather than at the first login. When
 * {@code app.jwt.aws.refresh-minutes} is greater than zero the secret is re-read
 * in the background of the next request after that interval, which is what makes
 * rotation possible without a restart. Public keys seen earlier are retained and
 * selected by the token's {@code kid}, so tokens issued just before a rotation
 * keep verifying until they expire.</p>
 *
 * <p>Credentials and region come from the standard AWS provider chain (IAM role,
 * environment, profile) — nothing secret is read from application configuration.</p>
 */
public class AwsSecretsManagerJwtKeyProvider implements JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerJwtKeyProvider.class);

    private static final String FIELD_PRIVATE_KEY = "privateKey";
    private static final String FIELD_PUBLIC_KEY = "publicKey";
    private static final String FIELD_KEY_ID = "keyId";

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    private final String secretId;
    private final Duration refreshInterval;

    /** Every public key this instance has seen, so a rotation does not break live tokens. */
    private final Map<String, RSAPublicKey> knownPublicKeys = new ConcurrentHashMap<>();

    private volatile JwtSigningKey signingKey;
    private volatile Instant loadedAt;

    public AwsSecretsManagerJwtKeyProvider(SecretsManagerClient secretsManagerClient,
                                           ObjectMapper objectMapper,
                                           JwtProperties.Aws aws) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
        this.secretId = aws.secretId();
        this.refreshInterval = aws.refreshMinutes() > 0
                ? Duration.ofMinutes(aws.refreshMinutes())
                : Duration.ZERO;
        load();
    }

    @Override
    public JwtSigningKey currentSigningKey() {
        refreshIfStale();
        return signingKey;
    }

    @Override
    public Optional<RSAPublicKey> verificationKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        RSAPublicKey known = knownPublicKeys.get(keyId);
        if (known != null) {
            return Optional.of(known);
        }
        // An unknown kid may simply mean the key rotated since this instance last looked.
        refreshIfStale();
        return Optional.ofNullable(knownPublicKeys.get(keyId));
    }

    private void refreshIfStale() {
        if (refreshInterval.isZero()) {
            return;
        }
        Instant loaded = loadedAt;
        if (loaded != null && Instant.now().isBefore(loaded.plus(refreshInterval))) {
            return;
        }
        synchronized (this) {
            if (loadedAt != null && Instant.now().isBefore(loadedAt.plus(refreshInterval))) {
                return;
            }
            try {
                load();
            } catch (RuntimeException ex) {
                // Keep serving with the cached key rather than failing every request.
                loadedAt = Instant.now();
                log.error("jwt_keys_refresh_failed secretId={} - continuing with the cached key pair", secretId, ex);
            }
        }
    }

    private void load() {
        String secretValue = fetchSecretValue();
        KeyMaterial material = parse(secretValue);

        RSAPrivateKey privateKey = RsaKeys.parsePrivateKey(material.privateKeyPem());
        RSAPublicKey publicKey = (material.publicKeyPem() == null || material.publicKeyPem().isBlank())
                ? RsaKeys.derivePublicKey(privateKey)
                : RsaKeys.parsePublicKey(material.publicKeyPem());
        String keyId = (material.keyId() == null || material.keyId().isBlank())
                ? RsaKeys.fingerprint(publicKey)
                : material.keyId();

        knownPublicKeys.put(keyId, publicKey);
        JwtSigningKey previous = signingKey;
        signingKey = new JwtSigningKey(keyId, privateKey, publicKey);
        loadedAt = Instant.now();

        if (previous == null) {
            log.info("jwt_keys_loaded source=aws-secrets-manager secretId={} keyId={}", secretId, keyId);
        } else if (!previous.keyId().equals(keyId)) {
            log.info("jwt_keys_rotated secretId={} previousKeyId={} keyId={}", secretId, previous.keyId(), keyId);
        }
    }

    private String fetchSecretValue() {
        try {
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            String value = response.secretString();
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "AWS Secrets Manager secret '" + secretId + "' has no string value");
            }
            return value;
        } catch (SecretsManagerException ex) {
            // The message names the secret and the AWS error, never the secret's contents.
            throw unreadable(ex.awsErrorDetails() == null ? ex.getMessage() : ex.awsErrorDetails().errorMessage(), ex);
        } catch (SdkException ex) {
            // Credential-chain, region and connectivity failures land here rather than
            // as a service error, and deserve the same clear startup message.
            throw unreadable(ex.getMessage(), ex);
        }
    }

    private IllegalStateException unreadable(String reason, RuntimeException cause) {
        return new IllegalStateException("The JWT signing key could not be read from AWS Secrets Manager secret '"
                + secretId + "': " + reason, cause);
    }

    private KeyMaterial parse(String secretValue) {
        String trimmed = secretValue.trim();
        if (!trimmed.startsWith("{")) {
            // A bare PEM private key; the public key is derived from it.
            return new KeyMaterial(trimmed, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String privateKeyPem = text(root, FIELD_PRIVATE_KEY);
            if (privateKeyPem == null) {
                throw new IllegalStateException("AWS Secrets Manager secret '" + secretId
                        + "' is JSON but has no '" + FIELD_PRIVATE_KEY + "' field");
            }
            return new KeyMaterial(privateKeyPem, text(root, FIELD_PUBLIC_KEY), text(root, FIELD_KEY_ID));
        } catch (JacksonException ex) {
            throw new IllegalStateException("AWS Secrets Manager secret '" + secretId
                    + "' starts with '{' but is not valid JSON", ex);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }

    private record KeyMaterial(String privateKeyPem, String publicKeyPem, String keyId) {
    }
}
