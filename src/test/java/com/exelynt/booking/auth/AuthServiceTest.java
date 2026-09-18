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
import com.exelynt.booking.auth.entity.RefreshToken;
import com.exelynt.booking.auth.service.AuthService;
import com.exelynt.booking.auth.service.RefreshTokenService;
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
    private RefreshTokenService refreshTokenService;
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
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

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
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("refresh rotates the token: the presented one is revoked and a new pair is issued")
    void refreshRotatesToken() {
        RefreshToken stored = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(7));
        when(refreshTokenService.find("raw-refresh")).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(user))
                .thenReturn(new IssuedAccessToken("new-access", "jti-2", Instant.now().plusSeconds(900), 900));
        when(refreshTokenService.issue(user)).thenReturn("new-refresh");

        TokenResponse response = authService.refresh("raw-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).revoke(stored);
        verify(auditService).record("alice", AuditAction.TOKEN_REFRESH, "User", 7L);
    }

    @Test
    @DisplayName("an already revoked refresh token is rejected")
    void refreshRejectsRevokedToken() {
        RefreshToken stored = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(7));
        stored.revoke();
        when(refreshTokenService.find("raw-refresh")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditService).record("alice", AuditAction.TOKEN_REFRESH_FAILURE, "User", null);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("an expired refresh token is rejected")
    void refreshRejectsExpiredToken() {
        RefreshToken stored = new RefreshToken(user, "hash", LocalDateTime.now().minusMinutes(1));
        when(refreshTokenService.find("raw-refresh")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("an unknown refresh token is rejected")
    void refreshRejectsUnknownToken() {
        when(refreshTokenService.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditService).record(eq(null), eq(AuditAction.TOKEN_REFRESH_FAILURE), eq("User"), eq(null));
    }

    @Test
    @DisplayName("logout revokes every refresh token and blacklists the presented access token")
    void logoutRevokesEverything() {
        Instant expiry = Instant.now().plusSeconds(600);

        authService.logout(7L, "alice", new JwtTokenDetails("jti-3", expiry));

        verify(refreshTokenService).revokeAllForUser(7L);
        verify(tokenBlacklist).blacklist("jti-3", expiry);
        verify(auditService).record("alice", AuditAction.LOGOUT, "User", 7L);
    }

    private Authentication mockAuthentication() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "alice", null, java.util.List.of());
    }
}
