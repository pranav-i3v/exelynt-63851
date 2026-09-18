package com.exelynt.booking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.auth.token.entity.RefreshToken;
import com.exelynt.booking.auth.repository.RefreshTokenRepository;
import com.exelynt.booking.auth.token.dto.IssuedRefreshToken;
import com.exelynt.booking.auth.token.dto.StoredRefreshToken;
import com.exelynt.booking.auth.token.impl.JpaRefreshTokenStore;
import com.exelynt.booking.security.config.JwtProperties;
import com.exelynt.booking.security.config.JwtProperties.Aws;
import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JpaRefreshTokenStoreTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JpaRefreshTokenStore store;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(15, 7, "resource-booking-system",
                new Aws("test/jwt-signing-key", "eu-west-1", 0L));
        store = new JpaRefreshTokenStore(refreshTokenRepository, properties);
        user = new User("alice", "$2a$10$hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    @DisplayName("the issued value is never what gets stored: the row holds a SHA-256 hash")
    void storesOnlyTheHash() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        IssuedRefreshToken issued = store.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(issued.value())
                .hasSize(64)
                .matches("[0-9a-f]+");
        assertThat(issued.value()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("two issued tokens differ")
    void issuesDistinctTokens() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(store.issue(user).value()).isNotEqualTo(store.issue(user).value());
    }

    @Test
    @DisplayName("a lookup hashes the presented value and reports the token's state")
    void findsByHashAndReportsState() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        IssuedRefreshToken issued = store.issue(user);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken entity = saved.getValue();
        when(refreshTokenRepository.findByTokenHash(entity.getTokenHash())).thenReturn(Optional.of(entity));

        StoredRefreshToken found = store.find(issued.value()).orElseThrow();

        assertThat(found.userId()).isEqualTo(7L);
        assertThat(found.username()).isEqualTo("alice");
        assertThat(found.revoked()).isFalse();
        assertThat(found.isUsable()).isTrue();
        assertThat(found.isReplayOfConsumedToken()).isFalse();
    }

    @Test
    @DisplayName("an unknown value resolves to nothing")
    void unknownTokenIsEmpty() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(store.find("never-issued")).isEmpty();
    }

    @Test
    @DisplayName("revoking marks the row rather than deleting it, so a replay stays detectable")
    void revokeKeepsTheRow() {
        RefreshToken entity = new RefreshToken(user, "hash", LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(entity));

        store.revoke(new StoredRefreshToken("hash", 7L, "alice", LocalDateTime.now().plusDays(7), false));

        assertThat(entity.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(entity);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("the purge removes expired tokens only")
    void purgeDropsExpiredOnly() {
        when(refreshTokenRepository.deleteExpiredBefore(any(LocalDateTime.class))).thenReturn(4);

        assertThat(store.purgeExpired()).isEqualTo(4);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("revoking a whole family delegates to one bulk update")
    void revokeAllForUser() {
        when(refreshTokenRepository.revokeAllForUser(7L)).thenReturn(3);

        assertThat(store.revokeAllForUser(7L)).isEqualTo(3);
    }
}
