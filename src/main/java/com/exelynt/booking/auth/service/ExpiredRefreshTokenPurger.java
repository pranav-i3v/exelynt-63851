package com.exelynt.booking.auth.service;

import com.exelynt.booking.auth.token.RefreshTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drops refresh tokens that are past their expiry.
 *
 * <p>Without this, every login and every rotation leaves a row behind for good:
 * the table grows forever even though nothing in it is usable. Only expired
 * tokens go — a revoked but unexpired token is kept on purpose, because it is
 * what lets {@code AuthService} spot a stolen token being replayed.</p>
 *
 * <p>Several instances running this at once is harmless: the delete is by
 * expiry, so a row another instance already removed is simply not there.</p>
 */
@Component
public class ExpiredRefreshTokenPurger {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRefreshTokenPurger.class);

    private final RefreshTokenStore refreshTokenStore;

    public ExpiredRefreshTokenPurger(RefreshTokenStore refreshTokenStore) {
        this.refreshTokenStore = refreshTokenStore;
    }

    @Scheduled(cron = "${app.auth.refresh-token-purge-cron:0 15 * * * *}")
    public void purgeExpiredTokens() {
        try {
            int purged = refreshTokenStore.purgeExpired();
            if (purged > 0) {
                log.info("refresh_tokens_purged count={}", purged);
            }
        } catch (RuntimeException ex) {
            // A failed cleanup must never take the scheduler down; the next run retries.
            log.error("refresh_token_purge_failed", ex);
        }
    }
}
