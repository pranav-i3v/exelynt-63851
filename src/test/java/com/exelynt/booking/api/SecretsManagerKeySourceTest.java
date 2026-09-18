package com.exelynt.booking.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the rule that AWS Secrets Manager is the only source of key material.
 *
 * <p>If the application ever grew a second way to obtain a key — a property, a
 * file, a generated pair — these assertions would stop matching the secret, and
 * a context that reached the real AWS would fail outright.</p>
 */
class SecretsManagerKeySourceTest extends IntegrationTestSupport {

    @Test
    @DisplayName("the signing key is the one that came out of the Secrets Manager secret")
    void signingKeyComesFromTheSecret() {
        assertThat(jwtKeyProvider.currentSigningKey().keyId())
                .isEqualTo(StubSecretsManagerConfiguration.KEY_ID);
        assertThat(jwtKeyProvider.verificationKey(StubSecretsManagerConfiguration.KEY_ID)).isPresent();
    }

    @Test
    @DisplayName("the only JwtKeyProvider in the context is the AWS Secrets Manager one")
    void awsIsTheOnlyKeyProvider() {
        assertThat(applicationContext.getBeansOfType(JwtKeyProvider.class).values())
                .singleElement()
                .isInstanceOf(com.exelynt.booking.security.jwt.provider.impl.AwsSecretsManagerJwtKeyProvider.class);
    }

    @Test
    @DisplayName("no property can inject key material: only the secret id is configurable")
    void noKeyMaterialInConfiguration() {
        assertThat(jwtProperties.aws().secretId()).isEqualTo(StubSecretsManagerConfiguration.SECRET_ID);
        // The properties record exposes no field that could carry a key.
        assertThat(java.util.Arrays.stream(jwtProperties.getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactlyInAnyOrder("accessExpiryMinutes", "refreshExpiryDays", "issuer", "aws");
    }
}
