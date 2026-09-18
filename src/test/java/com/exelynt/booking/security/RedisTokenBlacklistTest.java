package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import com.exelynt.booking.security.blacklist.RedisTokenBlacklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("a revoked jti is stored under a TTL matching the token's remaining life")
    void storesWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redisTemplate);

        blacklist.blacklist("jti-1", Instant.now().plusSeconds(600));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("booking:blacklist:jti:jti-1"), any(), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(570), Duration.ofSeconds(600));
    }

    @Test
    @DisplayName("an already expired token is not stored: its own expiry claim refuses it")
    void skipsAlreadyExpiredTokens() {
        RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redisTemplate);

        blacklist.blacklist("jti-old", Instant.now().minusSeconds(1));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("a key that is present means the token is revoked")
    void readsPresence() {
        RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redisTemplate);
        when(redisTemplate.hasKey("booking:blacklist:jti:jti-1")).thenReturn(true);
        when(redisTemplate.hasKey("booking:blacklist:jti:jti-2")).thenReturn(false);

        assertThat(blacklist.isBlacklisted("jti-1")).isTrue();
        assertThat(blacklist.isBlacklisted("jti-2")).isFalse();
    }

    @Test
    @DisplayName("null and blank ids are ignored")
    void ignoresBlankIds() {
        RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redisTemplate);

        blacklist.blacklist(null, Instant.now().plusSeconds(60));
        blacklist.blacklist("  ", Instant.now().plusSeconds(60));

        assertThat(blacklist.isBlacklisted(null)).isFalse();
        assertThat(blacklist.isBlacklisted("  ")).isFalse();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("a Redis outage surfaces rather than silently admitting a revoked token")
    void doesNotSwallowFailures() {
        RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redisTemplate);
        when(redisTemplate.hasKey(any())).thenThrow(new RedisConnectionFailureException("redis is away"));

        assertThatThrownBy(() -> blacklist.isBlacklisted("jti-1"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
