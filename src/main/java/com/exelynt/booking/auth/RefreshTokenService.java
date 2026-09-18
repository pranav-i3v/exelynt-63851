package com.exelynt.booking.auth;

import com.exelynt.booking.security.JwtProperties;
import com.exelynt.booking.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, looks up and revokes refresh tokens.
 *
 * <p>The value handed to the client is 64 bytes of {@link SecureRandom} output;
 * the database only ever sees its SHA-256 hash.</p>
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    /** Creates a token for {@code user} and returns the raw value, which is not stored. */
    @Transactional
    public String issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtProperties.refreshExpiryDays());
        refreshTokenRepository.save(new RefreshToken(user, hash(value), expiresAt));
        return value;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findUsable(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(RefreshToken::isUsable);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> find(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken));
    }

    @Transactional
    public void revoke(RefreshToken token) {
        token.revoke();
        refreshTokenRepository.save(token);
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId);
    }

    /** SHA-256 hex digest; deterministic so a presented token can be looked up. */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable in this JVM", ex);
        }
    }
}
