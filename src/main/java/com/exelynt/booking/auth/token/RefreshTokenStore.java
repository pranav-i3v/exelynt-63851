package com.exelynt.booking.auth.token;

import com.exelynt.booking.auth.token.dto.IssuedRefreshToken;
import com.exelynt.booking.auth.token.dto.StoredRefreshToken;
import com.exelynt.booking.user.entity.User;
import java.util.Optional;

/**
 * Where refresh tokens live.
 *
 * <p>The auth service depends only on this interface, so the JPA implementation
 * can be replaced by a Redis-backed one without touching the login, refresh or
 * logout logic — the same arrangement already used for {@code TokenBlacklist}.</p>
 *
 * <p>Two rules any implementation must honour:</p>
 * <ul>
 *   <li>Only a hash of the token value is stored, never the value itself.</li>
 *   <li>A revoked token stays retrievable until it expires naturally. Deleting
 *       it on rotation would destroy the evidence that a stolen copy is being
 *       replayed, which is the point of rotating in the first place.</li>
 * </ul>
 */
public interface RefreshTokenStore {

    /** Issues a token for {@code user}; the returned value is not persisted in the clear. */
    IssuedRefreshToken issue(User user);

    /** Looks a token up by its raw value, whether or not it is still usable. */
    Optional<StoredRefreshToken> find(String rawToken);

    /** Marks one token revoked. Implementations keep it retrievable until it expires. */
    void revoke(StoredRefreshToken token);

    /**
     * Revokes every token of a user; returns how many were still live.
     *
     * <p>This must take effect even when the caller goes on to fail the request.
     * Theft detection revokes the family and <em>then</em> returns 401, so an
     * implementation that joined the caller's transaction would have its
     * revocation rolled back by that very failure.</p>
     */
    int revokeAllForUser(Long userId);

    /** Drops tokens that are past their expiry; returns how many went. */
    int purgeExpired();
}
