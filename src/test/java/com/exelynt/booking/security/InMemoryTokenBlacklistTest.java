package com.exelynt.booking.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.exelynt.booking.security.blacklist.InMemoryTokenBlacklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryTokenBlacklistTest {

    private final InMemoryTokenBlacklist blacklist = new InMemoryTokenBlacklist();

    @Test
    @DisplayName("a blacklisted jti is reported as revoked until it expires")
    void blacklistsUntilExpiry() {
        blacklist.blacklist("jti-1", Instant.now().plusSeconds(300));

        assertThat(blacklist.isBlacklisted("jti-1")).isTrue();
        assertThat(blacklist.isBlacklisted("jti-2")).isFalse();
    }

    @Test
    @DisplayName("an entry past its expiry no longer blocks the token")
    void forgetsExpiredEntries() {
        blacklist.blacklist("jti-old", Instant.now().minusSeconds(1));

        assertThat(blacklist.isBlacklisted("jti-old")).isFalse();
    }

    @Test
    @DisplayName("null and blank ids are ignored")
    void ignoresBlankIds() {
        blacklist.blacklist(null, Instant.now().plusSeconds(60));
        blacklist.blacklist("  ", Instant.now().plusSeconds(60));

        assertThat(blacklist.isBlacklisted(null)).isFalse();
        assertThat(blacklist.isBlacklisted("  ")).isFalse();
    }
}
