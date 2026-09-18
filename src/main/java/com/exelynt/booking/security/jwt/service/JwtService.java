package com.exelynt.booking.security.jwt.service;

import com.exelynt.booking.security.jwt.provider.JwtKeyProvider;
import com.exelynt.booking.security.JwtProperties;
import com.exelynt.booking.security.JwtSigningKey;
import com.exelynt.booking.security.jwt.dto.IssuedAccessToken;
import com.exelynt.booking.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies RS256 access tokens.
 *
 * <p>Tokens are signed with the private half of the RSA key pair supplied by the
 * {@link JwtKeyProvider} and verified with the matching public half, selected by
 * the {@code kid} header. Claims: {@code sub} (username), {@code userId},
 * {@code role}, {@code jti}.</p>
 *
 * <p>Verification is strict: the algorithm must be RS256, and signature, issuer
 * and expiry are all enforced by the parser. Anything else surfaces as a
 * {@code JwtException}. Pinning the algorithm matters — without it a token
 * presenting {@code alg: none}, or an HMAC token signed with the (public) verification
 * key, would be a way in.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";

    private static final String REQUIRED_ALGORITHM = "RS256";

    private final JwtKeyProvider keyProvider;
    private final JwtProperties properties;
    private final JwtParser parser;

    public JwtService(JwtKeyProvider keyProvider, JwtProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
        this.parser = Jwts.parser()
                .keyLocator(new PublicKeyLocator())
                .sig().add(Jwts.SIG.RS256).and()
                .requireIssuer(properties.issuer())
                .build();
    }

    public IssuedAccessToken generateAccessToken(User user) {
        JwtSigningKey key = keyProvider.currentSigningKey();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.accessExpirySeconds());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .header().keyId(key.keyId()).and()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .id(jti)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedAccessToken(token, jti, expiresAt, properties.accessExpirySeconds());
    }

    /**
     * Parses and fully verifies a token.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, signed with
     *         an unexpected algorithm or an unknown key, issued by someone else,
     *         or expired
     */
    public Jws<Claims> parse(String token) {
        return parser.parseSignedClaims(token);
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public Long extractUserId(Claims claims) {
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        return userId == null ? null : userId.longValue();
    }

    public Instant extractExpiry(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null ? Instant.now() : expiration.toInstant();
    }

    /** Resolves the verification key from the token's {@code kid}, refusing anything but RS256. */
    private final class PublicKeyLocator extends LocatorAdapter<Key> {

        @Override
        protected Key locate(JwsHeader header) {
            if (!REQUIRED_ALGORITHM.equals(header.getAlgorithm())) {
                throw new UnsupportedJwtException(
                        "Access tokens must be signed with " + REQUIRED_ALGORITHM);
            }
            return keyProvider.verificationKey(header.getKeyId())
                    .orElseThrow(() -> new SignatureException(
                            "No verification key is known for this token's key id"));
        }
    }
}
