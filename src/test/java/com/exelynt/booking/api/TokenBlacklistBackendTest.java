package com.exelynt.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exelynt.booking.security.blacklist.RedisTokenBlacklist;
import com.exelynt.booking.security.blacklist.TokenBlacklist;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Guards the rule that Redis is the only place revoked access tokens are kept.
 *
 * <p>If an in-process blacklist ever came back, a logout would stop being
 * visible to the other instances — which no single-instance test would catch,
 * so the constraint is asserted directly.</p>
 */
class TokenBlacklistBackendTest extends IntegrationTestSupport {

    @Test
    @DisplayName("the only TokenBlacklist in the context is the Redis one")
    void redisIsTheOnlyBackend() {
        assertThat(applicationContext.getBeansOfType(TokenBlacklist.class).values())
                .singleElement()
                .isInstanceOf(RedisTokenBlacklist.class);
    }

    @Test
    @DisplayName("logout revokes through Redis, and the revoked token is refused")
    void logoutRevokesThroughRedis() throws Exception {
        String loginBody = login(USER_USERNAME, USER_PASSWORD);
        String accessToken = JsonPath.read(loginBody, "$.accessToken");

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));
    }
}
