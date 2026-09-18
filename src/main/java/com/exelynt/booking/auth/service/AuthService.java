package com.exelynt.booking.auth.service;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.auth.dto.LoginRequest;
import com.exelynt.booking.auth.dto.TokenResponse;
import com.exelynt.booking.auth.token.RefreshTokenStore;
import com.exelynt.booking.auth.token.dto.IssuedRefreshToken;
import com.exelynt.booking.auth.token.dto.StoredRefreshToken;
import com.exelynt.booking.common.exception.type.ServiceUnavailableException;
import com.exelynt.booking.common.exception.type.UnauthorizedException;
import com.exelynt.booking.security.jwt.dto.IssuedAccessToken;
import com.exelynt.booking.security.jwt.service.JwtService;
import com.exelynt.booking.security.jwt.dto.JwtTokenDetails;
import com.exelynt.booking.security.blacklist.TokenBlacklist;
import com.exelynt.booking.user.entity.User;
import com.exelynt.booking.user.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the token lifecycle: login, rotation on refresh and revocation on logout.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTITY_TYPE = "User";
    /** One message for unknown, expired, revoked and replayed tokens alike: no hints for a probe. */
    private static final String INVALID_REFRESH_TOKEN = "Refresh token is invalid, expired or already used";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RefreshTokenStore refreshTokenStore,
                       JwtService jwtService,
                       TokenBlacklist tokenBlacklist,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            auditService.record(request.username(), AuditAction.LOGIN_FAILURE, ENTITY_TYPE, null);
            log.info("login_failed username={}", request.username());
            // Same message for unknown user and wrong password: no account enumeration.
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        TokenResponse response = issueTokenPair(user);
        auditService.record(user.getUsername(), AuditAction.LOGIN_SUCCESS, ENTITY_TYPE, user.getId());
        log.info("login_succeeded username={}", user.getUsername());
        return response;
    }

    /**
     * Rotates a refresh token: the presented token is revoked and a brand new
     * pair is issued.
     *
     * <p>Replaying a token that rotation already consumed is treated as theft,
     * not as a mistake: rotation means a legitimate client always holds the
     * newest token, so a second use of an old one says two parties hold the
     * same credential. Every refresh token of that user is revoked, which ends
     * the attacker's session and the victim's alike — the victim logs in again,
     * the attacker cannot.</p>
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Optional<StoredRefreshToken> stored = refreshTokenStore.find(rawRefreshToken);
        if (stored.isEmpty()) {
            auditService.record(null, AuditAction.TOKEN_REFRESH_FAILURE, ENTITY_TYPE, null);
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        StoredRefreshToken token = stored.get();
        if (token.isReplayOfConsumedToken()) {
            int revoked = refreshTokenStore.revokeAllForUser(token.userId());
            auditService.record(token.username(), AuditAction.REFRESH_TOKEN_REUSE_DETECTED,
                    ENTITY_TYPE, token.userId());
            log.warn("refresh_token_reuse_detected username={} revokedTokens={} - "
                    + "all sessions for this user have been ended", token.username(), revoked);
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        if (!token.isUsable()) {
            auditService.record(token.username(), AuditAction.TOKEN_REFRESH_FAILURE, ENTITY_TYPE, token.userId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));
        refreshTokenStore.revoke(token);

        TokenResponse response = issueTokenPair(user);
        auditService.record(user.getUsername(), AuditAction.TOKEN_REFRESH, ENTITY_TYPE, user.getId());
        log.info("token_refreshed username={}", user.getUsername());
        return response;
    }

    /**
     * Revokes every refresh token of the caller and blacklists the access token
     * that was used for this call until it would have expired anyway.
     */
    @Transactional
    public void logout(Long userId, String username, JwtTokenDetails tokenDetails) {
        refreshTokenStore.revokeAllForUser(userId);
        if (tokenDetails != null) {
            try {
                tokenBlacklist.blacklist(tokenDetails.jti(), tokenDetails.expiresAt());
            } catch (RuntimeException ex) {
                // Refresh tokens are already revoked, but the access token would stay
                // usable for the rest of its life. Reporting success here would be a
                // lie, so the caller gets a 503 and can retry.
                throw new ServiceUnavailableException(
                        "Logout could not revoke the access token; please retry", ex);
            }
        }
        auditService.record(username, AuditAction.LOGOUT, ENTITY_TYPE, userId);
        log.info("logout username={}", username);
    }

    private TokenResponse issueTokenPair(User user) {
        IssuedAccessToken accessToken = jwtService.generateAccessToken(user);
        IssuedRefreshToken refreshToken = refreshTokenStore.issue(user);
        return TokenResponse.bearer(accessToken.token(), refreshToken.value(), accessToken.expiresInSeconds());
    }
}
