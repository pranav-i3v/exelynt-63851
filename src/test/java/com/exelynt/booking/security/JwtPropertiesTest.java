package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exelynt.booking.security.JwtProperties.Aws;
import com.exelynt.booking.security.JwtProperties.KeySource;
import com.exelynt.booking.security.JwtProperties.Pem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String ISSUER = "resource-booking-system";
    private static final Aws AWS = new Aws("booking/jwt-signing-key", "eu-west-1", 0L);
    private static final Pem NO_PEM = new Pem(null, null, null);

    @Test
    @DisplayName("an AWS Secrets Manager source with a secret id is accepted")
    void acceptsAwsSource() {
        assertThatCode(() -> new JwtProperties(KeySource.AWS_SECRETS_MANAGER, 15, 7, ISSUER, NO_PEM, AWS))
                .doesNotThrowAnyException();
        assertThat(new JwtProperties(KeySource.AWS_SECRETS_MANAGER, 15, 7, ISSUER, NO_PEM, AWS)
                .accessExpirySeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("AWS Secrets Manager is the default when no source is configured")
    void defaultsToAwsSecretsManager() {
        assertThat(new JwtProperties(null, 15, 7, ISSUER, NO_PEM, AWS).keySource())
                .isEqualTo(KeySource.AWS_SECRETS_MANAGER);
    }

    @Test
    @DisplayName("an AWS source without a secret id fails fast")
    void rejectsMissingSecretId() {
        assertThatThrownBy(() -> new JwtProperties(
                KeySource.AWS_SECRETS_MANAGER, 15, 7, ISSUER, NO_PEM, new Aws(null, "eu-west-1", 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_ID");
    }

    @Test
    @DisplayName("an unresolved ${JWT_SECRET_ID} placeholder is reported as a missing variable")
    void rejectsUnresolvedPlaceholder() {
        assertThatThrownBy(() -> new JwtProperties(
                KeySource.AWS_SECRETS_MANAGER, 15, 7, ISSUER, NO_PEM, new Aws("${JWT_SECRET_ID}", null, 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_ID is not set");
    }

    @Test
    @DisplayName("a PEM source without a private key fails fast")
    void rejectsPemSourceWithoutKey() {
        assertThatThrownBy(() -> new JwtProperties(KeySource.PEM, 15, 7, ISSUER, NO_PEM, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    @DisplayName("the generated source needs no key configuration")
    void generatedSourceNeedsNoConfiguration() {
        assertThatCode(() -> new JwtProperties(KeySource.GENERATED, 15, 7, ISSUER, NO_PEM, new Aws(null, null, 0L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("non-positive expiries fail fast")
    void rejectsNonPositiveExpiries() {
        assertThatThrownBy(() -> new JwtProperties(KeySource.GENERATED, 0, 7, ISSUER, NO_PEM, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ACCESS_EXPIRY_MINUTES");
        assertThatThrownBy(() -> new JwtProperties(KeySource.GENERATED, 15, 0, ISSUER, NO_PEM, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_EXPIRY_DAYS");
    }

    @Test
    @DisplayName("a missing issuer fails fast")
    void rejectsMissingIssuer() {
        assertThatThrownBy(() -> new JwtProperties(KeySource.GENERATED, 15, 7, "  ", NO_PEM, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }
}
