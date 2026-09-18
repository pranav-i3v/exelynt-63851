package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exelynt.booking.security.JwtProperties.Aws;
import com.exelynt.booking.security.jwt.dto.IssuedAccessToken;
import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import com.exelynt.booking.security.jwt.service.JwtService;
import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String ISSUER = "resource-booking-system";

    private TestKeyProvider keyProvider;
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        keyProvider = new TestKeyProvider();
        jwtService = new JwtService(keyProvider, properties());
        user = new User("alice", "$2a$10$irrelevant", Role.USER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    @DisplayName("a generated token carries sub, userId, role, jti and the signing kid")
    void generatesExpectedClaims() {
        IssuedAccessToken issued = jwtService.generateAccessToken(user);

        Jws<Claims> parsed = jwtService.parse(issued.token());
        Claims claims = parsed.getPayload();
        assertThat(jwtService.extractUsername(claims)).isEqualTo("alice");
        assertThat(jwtService.extractUserId(claims)).isEqualTo(42L);
        assertThat(jwtService.extractRole(claims)).isEqualTo("USER");
        assertThat(jwtService.extractJti(claims)).isEqualTo(issued.jti());
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo(keyProvider.currentSigningKey().keyId());
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
        String expired = signed(keyProvider.currentSigningKey().privateKey(),
                keyProvider.currentSigningKey().keyId(), ISSUER, Instant.now().minusSeconds(120));

        assertThatThrownBy(() -> jwtService.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a token signed with another RSA key is rejected")
    void rejectsWrongSignature() {
        TestKeyProvider attacker = new TestKeyProvider();
        // Same kid as ours, so the key is found - but the signature will not verify.
        String foreign = signed(attacker.currentSigningKey().privateKey(),
                keyProvider.currentSigningKey().keyId(), ISSUER, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token whose kid is unknown is rejected")
    void rejectsUnknownKeyId() {
        String unknownKid = signed(keyProvider.currentSigningKey().privateKey(),
                "some-other-key", ISSUER, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtService.parse(unknownKid)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an HS256 token signed with the public key is rejected (algorithm confusion)")
    void rejectsAlgorithmConfusion() {
        // The classic RS256 attack: take the public key, which anybody may hold,
        // and present it as an HMAC secret.
        byte[] publicKeyBytes = Base64.getEncoder()
                .encode(keyProvider.currentSigningKey().publicKey().getEncoded());
        SecretKey hmacKey = Keys.hmacShaKeyFor(new String(publicKeyBytes, StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .header().keyId(keyProvider.currentSigningKey().keyId()).and()
                .issuer(ISSUER)
                .subject("alice")
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_ROLE, "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(hmacKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an unsigned token is rejected")
    void rejectsUnsignedToken() {
        String unsigned = Jwts.builder()
                .issuer(ISSUER)
                .subject("alice")
                .claim(JwtService.CLAIM_ROLE, "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .compact();

        assertThatThrownBy(() -> jwtService.parse(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void rejectsWrongIssuer() {
        String foreign = signed(keyProvider.currentSigningKey().privateKey(),
                keyProvider.currentSigningKey().keyId(), "someone-else", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a malformed token is rejected")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parse("not.a.jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a token stays verifiable after the signing key rotates")
    void verifiesTokensIssuedBeforeRotation() {
        JwtSigningKey firstKey = keyProvider.currentSigningKey();
        String issuedBeforeRotation = jwtService.generateAccessToken(user).token();

        RotatingKeyProvider rotating = new RotatingKeyProvider(firstKey);
        JwtService afterRotation = new JwtService(rotating, properties());
        rotating.rotate();

        // The new key signs new tokens ...
        assertThat(afterRotation.parse(afterRotation.generateAccessToken(user).token())
                .getHeader().getKeyId()).isEqualTo(rotating.currentSigningKey().keyId());
        // ... while the previous public key still verifies tokens already out there.
        assertThat(afterRotation.parse(issuedBeforeRotation).getPayload().getSubject()).isEqualTo("alice");
    }

    private static JwtProperties properties() {
        return new JwtProperties(15, 7, ISSUER, new Aws("test/jwt-signing-key", "eu-west-1", 0L));
    }

    private static String signed(PrivateKey key, String keyId, String issuer, Instant expiresAt) {
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject("alice")
                .id(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_USER_ID, 42L)
                .claim(JwtService.CLAIM_ROLE, "USER")
                .issuedAt(Date.from(expiresAt.minusSeconds(60)))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    /** Mimics an AWS-side rotation: a new signing key, old public keys retained. */
    private static final class RotatingKeyProvider implements JwtKeyProvider {

        private final java.util.Map<String, java.security.interfaces.RSAPublicKey> known =
                new java.util.concurrent.ConcurrentHashMap<>();
        private volatile JwtSigningKey current;

        private RotatingKeyProvider(JwtSigningKey initial) {
            this.current = initial;
            known.put(initial.keyId(), initial.publicKey());
        }

        private void rotate() {
            JwtSigningKey next = new TestKeyProvider().currentSigningKey();
            known.put(next.keyId(), next.publicKey());
            current = next;
        }

        @Override
        public JwtSigningKey currentSigningKey() {
            return current;
        }

        @Override
        public java.util.Optional<java.security.interfaces.RSAPublicKey> verificationKey(String keyId) {
            return java.util.Optional.ofNullable(known.get(keyId));
        }
    }

    /** Stands in for the AWS-backed provider; key handling itself is tested in its own suite. */
    private static final class TestKeyProvider implements JwtKeyProvider {

        private final JwtSigningKey signingKey;

        private TestKeyProvider() {
            java.security.KeyPair keyPair = TestRsaKeys.generateKeyPair();
            java.security.interfaces.RSAPublicKey publicKey =
                    (java.security.interfaces.RSAPublicKey) keyPair.getPublic();
            this.signingKey = new JwtSigningKey(RsaKeys.fingerprint(publicKey),
                    (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(), publicKey);
        }

        @Override
        public JwtSigningKey currentSigningKey() {
            return signingKey;
        }

        @Override
        public java.util.Optional<java.security.interfaces.RSAPublicKey> verificationKey(String keyId) {
            return signingKey.keyId().equals(keyId)
                    ? java.util.Optional.of(signingKey.publicKey())
                    : java.util.Optional.empty();
        }
    }
}
