package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exelynt.booking.user.Role;
import com.exelynt.booking.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-value-that-is-long-enough-123456";
    private static final String OTHER_SECRET = "another-unit-test-secret-of-at-least-32-bytes-xx";
    private static final String ISSUER = "resource-booking-system";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 15, 7, ISSUER));
        user = new User("alice", "$2a$10$irrelevant", Role.USER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    @DisplayName("a generated token carries sub, userId, role and jti")
    void generatesExpectedClaims() {
        IssuedAccessToken issued = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parse(issued.token()).getPayload();
        assertThat(jwtService.extractUsername(claims)).isEqualTo("alice");
        assertThat(jwtService.extractUserId(claims)).isEqualTo(42L);
        assertThat(jwtService.extractRole(claims)).isEqualTo("USER");
        assertThat(jwtService.extractJti(claims)).isEqualTo(issued.jti());
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("every token gets its own jti")
    void mintsUniqueJti() {
        assertThat(jwtService.generateAccessToken(user).jti())
                .isNotEqualTo(jwtService.generateAccessToken(user).jti());
    }

    @Test
    @DisplayName("a token that has expired is rejected")
    void rejectsExpiredToken() {
        String expired = tokenWith(key(SECRET), ISSUER, Instant.now().minusSeconds(120));

        assertThatThrownBy(() -> jwtService.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void rejectsWrongSignature() {
        String foreign = tokenWith(key(OTHER_SECRET), ISSUER, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void rejectsWrongIssuer() {
        String foreign = tokenWith(key(SECRET), "someone-else", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a malformed token is rejected")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parse("not.a.jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parsing a valid token returns verified claims")
    void parsesValidToken() {
        Jws<Claims> parsed = jwtService.parse(jwtService.generateAccessToken(user).token());

        assertThat(parsed.getPayload().getIssuer()).isEqualTo(ISSUER);
        assertThat(jwtService.extractExpiry(parsed.getPayload())).isAfter(Instant.now());
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String tokenWith(SecretKey signingKey, String issuer, Instant expiresAt) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("alice")
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_USER_ID, 42L)
                .claim(JwtService.CLAIM_ROLE, "USER")
                .issuedAt(Date.from(expiresAt.minusSeconds(60)))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
