package com.exelynt.booking.auth.service;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.auth.dto.LoginRequest;
import com.exelynt.booking.auth.dto.TokenResponse;
import com.exelynt.booking.auth.entity.RefreshToken;
import com.exelynt.booking.common.exception.type.UnauthorizedException;
import com.exelynt.booking.security.IssuedAccessToken;
import com.exelynt.booking.security.JwtService;
import com.exelynt.booking.security.JwtTokenDetails;
import com.exelynt.booking.security.TokenBlacklist;
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

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RefreshTokenService refreshTokenService,
                       JwtService jwtService,
                       TokenBlacklist tokenBlacklist,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
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
     * pair is issued. Unknown, revoked and expired tokens are all rejected with
     * the same 401.
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Optional<RefreshToken> stored = refreshTokenService.find(rawRefreshToken);
        if (stored.isEmpty() || !stored.get().isUsable()) {
            String actor = stored.map(token -> token.getUser().getUsername()).orElse(null);
            auditService.record(actor, AuditAction.TOKEN_REFRESH_FAILURE, ENTITY_TYPE, null);
            throw new UnauthorizedException("Refresh token is invalid, expired or already used");
        }

        RefreshToken token = stored.get();
        User user = token.getUser();
        refreshTokenService.revoke(token);

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
        refreshTokenService.revokeAllForUser(userId);
        if (tokenDetails != null) {
            tokenBlacklist.blacklist(tokenDetails.jti(), tokenDetails.expiresAt());
        }
        auditService.record(username, AuditAction.LOGOUT, ENTITY_TYPE, userId);
        log.info("logout username={}", username);
    }

    private TokenResponse issueTokenPair(User user) {
        IssuedAccessToken accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return TokenResponse.bearer(accessToken.token(), refreshToken, accessToken.expiresInSeconds());
    }
}
