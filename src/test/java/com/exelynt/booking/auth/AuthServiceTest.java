package com.exelynt.booking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.auth.dto.LoginRequest;
import com.exelynt.booking.auth.dto.TokenResponse;
import com.exelynt.booking.auth.service.AuthService;
import com.exelynt.booking.auth.token.RefreshTokenStore;
import com.exelynt.booking.auth.token.dto.IssuedRefreshToken;
import com.exelynt.booking.auth.token.dto.StoredRefreshToken;
import com.exelynt.booking.common.exception.type.UnauthorizedException;
import com.exelynt.booking.security.jwt.dto.IssuedAccessToken;
import com.exelynt.booking.security.jwt.service.JwtService;
import com.exelynt.booking.security.JwtTokenDetails;
import com.exelynt.booking.security.TokenBlacklist;
import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import com.exelynt.booking.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "$2a$10$hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    @DisplayName("a successful login returns a token pair and is audited")
    void loginIssuesTokenPair() {
        when(authenticationManager.authenticate(any())).thenReturn(mockAuthentication());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user))
                .thenReturn(new IssuedAccessToken("access-token", "jti-1", Instant.now().plusSeconds(900), 900));
        when(refreshTokenStore.issue(user))
                .thenReturn(new IssuedRefreshToken("refresh-token", LocalDateTime.now().plusDays(7)));

        TokenResponse response = authService.login(new LoginRequest("alice", "Secret@123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        verify(auditService).record("alice", AuditAction.LOGIN_SUCCESS, "User", 7L);
    }

    @Test
    @DisplayName("bad credentials give a generic 401 and are audited as a failure")
    void loginFailureIsAudited() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");

        verify(auditService).record("alice", AuditAction.LOGIN_FAILURE, "User", null);
        verify(refreshTokenStore, never()).issue(any());
    }

    @Test
    @DisplayName("refresh rotates the token: the presented one is revoked and a new pair is issued")
    void refreshRotatesToken() {
        StoredRefreshToken stored = usableToken();
        when(refreshTokenStore.find("raw-refresh")).thenReturn(Optional.of(stored));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user))
                .thenReturn(new IssuedAccessToken("new-access", "jti-2", Instant.now().plusSeconds(900), 900));
        when(refreshTokenStore.issue(user))
                .thenReturn(new IssuedRefreshToken("new-refresh", LocalDateTime.now().plusDays(7)));

        TokenResponse response = authService.refresh("raw-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore).revoke(stored);
        verify(auditService).record("alice", AuditAction.TOKEN_REFRESH, "User", 7L);
    }

    @Test
    @DisplayName("replaying an already rotated token revokes every session of that user")
    void refreshReuseRevokesTheWholeFamily() {
        // A revoked but unexpired token coming back means two parties hold it.
        StoredRefreshToken replayed = new StoredRefreshToken(
                "hash", 7L, "alice", LocalDateTime.now().plusDays(7), true);
        when(refreshTokenStore.find("stolen-token")).thenReturn(Optional.of(replayed));
        when(refreshTokenStore.revokeAllForUser(7L)).thenReturn(2);

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token is invalid, expired or already used");

        verify(refreshTokenStore).revokeAllForUser(7L);
        verify(auditService).record("alice", AuditAction.REFRESH_TOKEN_REUSE_DETECTED, "User", 7L);
        // No new pair is handed out to whoever presented the stolen token.
        verify(refreshTokenStore, never()).issue(any());
    }

    @Test
    @DisplayName("an expired token is refused but is not treated as theft")
    void refreshRejectsExpiredTokenWithoutRevokingEverything() {
        StoredRefreshToken expired = new StoredRefreshToken(
                "hash", 7L, "alice", LocalDateTime.now().minusMinutes(1), false);
        when(refreshTokenStore.find("raw-refresh")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditService).record("alice", AuditAction.TOKEN_REFRESH_FAILURE, "User", 7L);
        verify(refreshTokenStore, never()).revokeAllForUser(any());
        verify(refreshTokenStore, never()).issue(any());
    }

    @Test
    @DisplayName("an expired token that was also revoked is not mistaken for a replay")
    void expiredAndRevokedIsNotTreatedAsReuse() {
        StoredRefreshToken old = new StoredRefreshToken(
                "hash", 7L, "alice", LocalDateTime.now().minusDays(1), true);
        when(refreshTokenStore.find("raw-refresh")).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenStore, never()).revokeAllForUser(any());
        verify(auditService).record("alice", AuditAction.TOKEN_REFRESH_FAILURE, "User", 7L);
    }


    @Test
    @DisplayName("an unknown refresh token is rejected and revokes nothing")
    void refreshRejectsUnknownToken() {
        when(refreshTokenStore.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditService).record(eq(null), eq(AuditAction.TOKEN_REFRESH_FAILURE), eq("User"), eq(null));
        verify(refreshTokenStore, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("logout revokes every refresh token and blacklists the presented access token")
    void logoutRevokesEverything() {
        Instant expiry = Instant.now().plusSeconds(600);

        authService.logout(7L, "alice", new JwtTokenDetails("jti-3", expiry));

        verify(refreshTokenStore).revokeAllForUser(7L);
        verify(tokenBlacklist).blacklist("jti-3", expiry);
        verify(auditService).record("alice", AuditAction.LOGOUT, "User", 7L);
    }

    private Authentication mockAuthentication() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "alice", null, java.util.List.of());
    }

    private StoredRefreshToken usableToken() {
        return new StoredRefreshToken("hash", 7L, "alice", LocalDateTime.now().plusDays(7), false);
    }
}
