package com.exelynt.booking.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;

/**
 * What happens when the only blacklist backend is unreachable.
 *
 * <p>Redis is the sole record of which access tokens were revoked, so a backend
 * that cannot answer must not result in the request being admitted. These tests
 * pin the fail-closed behaviour, and that it is reported as 503 rather than as a
 * 401 the client would misread as "log in again".</p>
 */
class BlacklistOutageTest extends IntegrationTestSupport {

    @Autowired
    private StringRedisTemplate stubRedis;

    @AfterEach
    void restoreRedis() {
        reset(stubRedis);
    }

    @Test
    @DisplayName("an authenticated request is refused with 503 while the blacklist is unreachable")
    void requestsFailClosedDuringAnOutage() throws Exception {
        String accessToken = accessToken(USER_USERNAME, USER_PASSWORD);
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        when(stubRedis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis is away"));

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Token revocation check is temporarily unavailable"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("logout reports 503 rather than claiming to have revoked the token")
    void logoutDoesNotClaimFalseSuccess() throws Exception {
        String accessToken = JsonPath.read(login(USER_USERNAME, USER_PASSWORD), "$.accessToken");

        when(stubRedis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis is away"));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Logout could not revoke the access token; please retry"));
    }
}
