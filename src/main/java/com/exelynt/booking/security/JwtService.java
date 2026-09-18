package com.exelynt.booking.security;

import com.exelynt.booking.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies HS256 access tokens.
 *
 * <p>Claims: {@code sub} (username), {@code userId}, {@code role}, {@code jti}.
 * Verification is strict: signature, issuer and expiry are all enforced by the
 * parser, and any failure surfaces as a {@code JwtException}.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build();
    }

    public IssuedAccessToken generateAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.accessExpirySeconds());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .id(jti)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, jti, expiresAt, properties.accessExpirySeconds());
    }

    /**
     * Parses and fully verifies a token.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, unsigned,
     *         signed with the wrong key, issued by someone else or expired
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
}
