package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "unit-test-secret-value-that-is-long-enough-123456";
    private static final String ISSUER = "resource-booking-system";

    @Test
    @DisplayName("a secret of at least 32 bytes is accepted")
    void acceptsStrongSecret() {
        assertThatCode(() -> new JwtProperties(VALID_SECRET, 15, 7, ISSUER)).doesNotThrowAnyException();
        assertThat(new JwtProperties(VALID_SECRET, 15, 7, ISSUER).accessExpirySeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes fails fast")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", 15, 7, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("a missing secret fails fast")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties("   ", 15, 7, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be provided");
    }

    @Test
    @DisplayName("an unresolved ${JWT_SECRET} placeholder is reported as a missing variable")
    void rejectsUnresolvedPlaceholder() {
        assertThatThrownBy(() -> new JwtProperties("${JWT_SECRET}", 15, 7, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    @DisplayName("non-positive expiries fail fast")
    void rejectsNonPositiveExpiries() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, 0, 7, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ACCESS_EXPIRY_MINUTES");
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 0, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_EXPIRY_DAYS");
    }
}
