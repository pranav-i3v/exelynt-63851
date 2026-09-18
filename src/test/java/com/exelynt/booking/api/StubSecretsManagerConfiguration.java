package com.exelynt.booking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exelynt.booking.security.TestRsaKeys;
import java.security.KeyPair;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Replaces the AWS client, not the key provider.
 *
 * <p>Integration tests still run the real {@code AwsSecretsManagerJwtKeyProvider}
 * against a secret in the real JSON shape, so the production key path — fetch,
 * parse, derive, fingerprint — is the one under test. Only the network call to
 * AWS is stubbed, and the key pair is generated per JVM run, so no key material
 * is committed.</p>
 */
@TestConfiguration
public class StubSecretsManagerConfiguration {

    public static final String SECRET_ID = "test/jwt-signing-key";
    public static final String KEY_ID = "test-key-1";

    private static final KeyPair KEY_PAIR = TestRsaKeys.generateKeyPair();

    /**
     * Marked primary so the key provider injects this client rather than the real
     * one. Verified by {@code SecretsManagerStubTest}: if the production client
     * ever won, the context would fail trying to reach AWS.
     */
    @Bean
    @Primary
    public SecretsManagerClient stubSecretsManagerClient() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .name(SECRET_ID)
                        .secretString(TestRsaKeys.secretJson(KEY_PAIR, KEY_ID))
                        .build());
        return client;
    }
}
