package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exelynt.booking.security.JwtProperties.Aws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String ISSUER = "resource-booking-system";
    private static final Aws AWS = new Aws("booking/jwt-signing-key", "eu-west-1", 0L);

    @Test
    @DisplayName("a secret id is all the key configuration there is")
    void acceptsSecretId() {
        assertThatCode(() -> new JwtProperties(15, 7, ISSUER, AWS)).doesNotThrowAnyException();
        assertThat(new JwtProperties(15, 7, ISSUER, AWS).accessExpirySeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("a missing secret id fails fast: there is no fallback key source")
    void rejectsMissingSecretId() {
        assertThatThrownBy(() -> new JwtProperties(15, 7, ISSUER, new Aws(null, "eu-west-1", 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_ID");
        assertThatThrownBy(() -> new JwtProperties(15, 7, ISSUER, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_ID");
    }

    @Test
    @DisplayName("an unresolved ${JWT_SECRET_ID} placeholder is reported as a missing variable")
    void rejectsUnresolvedPlaceholder() {
        assertThatThrownBy(() -> new JwtProperties(15, 7, ISSUER, new Aws("${JWT_SECRET_ID}", null, 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_ID is not set");
    }

    @Test
    @DisplayName("non-positive expiries fail fast")
    void rejectsNonPositiveExpiries() {
        assertThatThrownBy(() -> new JwtProperties(0, 7, ISSUER, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ACCESS_EXPIRY_MINUTES");
        assertThatThrownBy(() -> new JwtProperties(15, 0, ISSUER, AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_EXPIRY_DAYS");
    }

    @Test
    @DisplayName("a missing issuer fails fast")
    void rejectsMissingIssuer() {
        assertThatThrownBy(() -> new JwtProperties(15, 7, "  ", AWS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }
}
