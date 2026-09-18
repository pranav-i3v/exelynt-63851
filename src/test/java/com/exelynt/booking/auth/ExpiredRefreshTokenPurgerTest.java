package com.exelynt.booking.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.auth.service.ExpiredRefreshTokenPurger;
import com.exelynt.booking.auth.token.RefreshTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiredRefreshTokenPurgerTest {

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private ExpiredRefreshTokenPurger purger;

    @Test
    @DisplayName("the scheduled sweep asks the store to drop expired tokens")
    void purges() {
        when(refreshTokenStore.purgeExpired()).thenReturn(7);

        purger.purgeExpiredTokens();

        verify(refreshTokenStore).purgeExpired();
    }

    @Test
    @DisplayName("a failing sweep is logged, not propagated, so the scheduler keeps running")
    void survivesFailure() {
        when(refreshTokenStore.purgeExpired()).thenThrow(new IllegalStateException("database is away"));

        assertThatCode(() -> purger.purgeExpiredTokens()).doesNotThrowAnyException();
    }
}
