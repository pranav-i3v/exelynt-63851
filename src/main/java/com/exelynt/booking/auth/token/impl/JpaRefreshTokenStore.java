package com.exelynt.booking.auth.token.impl;

import com.exelynt.booking.auth.entity.RefreshToken;
import com.exelynt.booking.auth.repository.RefreshTokenRepository;
import com.exelynt.booking.auth.token.RefreshTokenStore;
import com.exelynt.booking.auth.token.dto.IssuedRefreshToken;
import com.exelynt.booking.auth.token.dto.StoredRefreshToken;
import com.exelynt.booking.security.JwtProperties;
import com.exelynt.booking.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relational {@link RefreshTokenStore}.
 *
 * <p>The value handed to the client is 64 bytes of {@link SecureRandom} output;
 * the database only ever sees its SHA-256 hash, so a database dump cannot be
 * replayed against the API.</p>
 */
@Component
public class JpaRefreshTokenStore implements RefreshTokenStore {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JpaRefreshTokenStore(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtProperties.refreshExpiryDays());
        refreshTokenRepository.save(new RefreshToken(user, hash(value), expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredRefreshToken> find(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).map(JpaRefreshTokenStore::toView);
    }

    @Override
    @Transactional
    public void revoke(StoredRefreshToken token) {
        refreshTokenRepository.findByTokenHash(token.tokenHash()).ifPresent(entity -> {
            entity.revoke();
            refreshTokenRepository.save(entity);
        });
    }

    /**
     * Runs in its own transaction, as the interface requires: the caller revokes
     * a family on theft detection and then throws, which would otherwise roll
     * back the revocation along with the failed request.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId);
    }

    @Override
    @Transactional
    public int purgeExpired() {
        // Only past-expiry rows go. Revoked-but-unexpired rows stay, because they
        // are what makes replay of a rotated token detectable.
        return refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now());
    }

    private static StoredRefreshToken toView(RefreshToken entity) {
        return new StoredRefreshToken(
                entity.getTokenHash(),
                entity.getUser().getId(),
                entity.getUser().getUsername(),
                entity.getExpiresAt(),
                entity.isRevoked());
    }

    /** SHA-256 hex digest; deterministic so a presented token can be looked up. */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable in this JVM", ex);
        }
    }
}
