package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.security.JwtProperties.Aws;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import com.exelynt.booking.security.jwt.provider.impl.AwsSecretsManagerJwtKeyProvider;
import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerJwtKeyProviderTest {

    private static final String SECRET_ID = "booking/jwt-signing-key";

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = RsaKeys.generateKeyPair();
    }

    @Test
    @DisplayName("a JSON secret with both keys and an explicit key id is loaded")
    void loadsJsonSecret() {
        stubSecret("""
                {"privateKey":"%s","publicKey":"%s","keyId":"booking-2026-09"}
                """.formatted(escaped(privateKeyPem()), escaped(publicKeyPem())));

        JwtKeyProvider provider = newProvider(0L);

        JwtSigningKey signingKey = provider.currentSigningKey();
        assertThat(signingKey.keyId()).isEqualTo("booking-2026-09");
        assertThat(signingKey.privateKey().getModulus())
                .isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
        assertThat(provider.verificationKey("booking-2026-09")).isPresent();
        assertThat(provider.verificationKey("unknown-key")).isEmpty();
    }

    @Test
    @DisplayName("a bare PEM secret works and the public key is derived from it")
    void loadsBarePemSecret() {
        stubSecret(privateKeyPem());

        JwtSigningKey signingKey = newProvider(0L).currentSigningKey();

        assertThat(signingKey.publicKey().getModulus())
                .isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus());
        // With no key id in the secret, a stable fingerprint is used instead.
        assertThat(signingKey.keyId()).isEqualTo(RsaKeys.fingerprint((RSAPublicKey) keyPair.getPublic()));
    }

    @Test
    @DisplayName("the secret is read once at startup and then cached")
    void cachesTheSecret() {
        stubSecret(privateKeyPem());

        JwtKeyProvider provider = newProvider(0L);
        provider.currentSigningKey();
        provider.currentSigningKey();

        verify(secretsManagerClient, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    @DisplayName("a missing secret fails fast at construction")
    void failsFastWhenSecretIsMissing() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(ResourceNotFoundException.builder()
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Secrets Manager can't find it").build())
                        .message("not found")
                        .build());

        assertThatThrownBy(() -> newProvider(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SECRET_ID)
                .hasMessageContaining("could not be read");
    }

    @Test
    @DisplayName("a secret with no string value is rejected")
    void rejectsEmptySecret() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("   ").build());

        assertThatThrownBy(() -> newProvider(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no string value");
    }

    @Test
    @DisplayName("JSON without a privateKey field is rejected")
    void rejectsJsonWithoutPrivateKey() {
        stubSecret("{\"publicKey\":\"whatever\"}");

        assertThatThrownBy(() -> newProvider(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("privateKey");
    }

    @Test
    @DisplayName("malformed JSON is rejected without echoing the secret")
    void rejectsMalformedJson() {
        stubSecret("{not json at all");

        assertThatThrownBy(() -> newProvider(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON")
                .hasMessageNotContaining("not json at all");
    }

    @Test
    @DisplayName("a rotated secret is picked up and the previous public key still verifies")
    void picksUpRotationAndKeepsPreviousKey() {
        KeyPair rotated = RsaKeys.generateKeyPair();
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(privateKeyPem()).build())
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString(pem("PRIVATE KEY", rotated.getPrivate().getEncoded())).build());

        JwtKeyProvider provider = newProvider(1L);
        String originalKeyId = provider.currentSigningKey().keyId();

        expireCache(provider);
        JwtSigningKey afterRotation = provider.currentSigningKey();

        assertThat(afterRotation.keyId()).isNotEqualTo(originalKeyId);
        assertThat(afterRotation.publicKey().getModulus())
                .isEqualTo(((RSAPublicKey) rotated.getPublic()).getModulus());
        // Tokens issued just before the rotation must keep verifying until they expire.
        assertThat(provider.verificationKey(originalKeyId)).isPresent();
        assertThat(provider.verificationKey(afterRotation.keyId())).isPresent();
    }

    @Test
    @DisplayName("an unknown key id triggers a re-read, in case the key rotated elsewhere")
    void unknownKeyIdTriggersReload() {
        KeyPair rotated = RsaKeys.generateKeyPair();
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(privateKeyPem()).build())
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString(pem("PRIVATE KEY", rotated.getPrivate().getEncoded())).build());

        JwtKeyProvider provider = newProvider(1L);
        String rotatedKeyId = RsaKeys.fingerprint((RSAPublicKey) rotated.getPublic());
        expireCache(provider);

        assertThat(provider.verificationKey(rotatedKeyId)).isPresent();
    }

    @Test
    @DisplayName("a failed refresh keeps serving the cached key instead of failing requests")
    void failedRefreshFallsBackToCachedKey() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(privateKeyPem()).build())
                .thenThrow(ResourceNotFoundException.builder()
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("temporarily gone").build())
                        .message("not found")
                        .build());

        JwtKeyProvider provider = newProvider(1L);
        String originalKeyId = provider.currentSigningKey().keyId();
        expireCache(provider);

        assertThat(provider.currentSigningKey().keyId()).isEqualTo(originalKeyId);
    }

    /** Backdates the cache so the next lookup re-reads the secret. */
    private static void expireCache(JwtKeyProvider provider) {
        ReflectionTestUtils.setField(provider, "loadedAt", Instant.now().minus(Duration.ofHours(1)));
    }

    private JwtKeyProvider newProvider(long refreshMinutes) {
        return new AwsSecretsManagerJwtKeyProvider(
                secretsManagerClient, objectMapper, new Aws(SECRET_ID, "eu-west-1", refreshMinutes));
    }

    private void stubSecret(String value) {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(value).build());
    }

    private String privateKeyPem() {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private String publicKeyPem() {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private static String escaped(String pem) {
        return pem.replace("\n", "\\n");
    }
}
