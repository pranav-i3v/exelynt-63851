package com.exelynt.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exelynt.booking.common.logging.CorrelationIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthenticationApiTest extends IntegrationTestSupport {

    @Test
    @DisplayName("login returns an access token, a refresh token and the lifetime")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("login with a wrong password is a 401 in the standard error shape")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("login with an unknown user gives the same 401, without leaking existence")
    void loginFailsWithUnknownUser() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("login without credentials is a 400 listing the offending fields")
    void loginValidatesInput() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("a request without a token is a JSON 401")
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("an expired token is a JSON 401")
    void expiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken("admin"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    @DisplayName("a malformed token is a JSON 401")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token is invalid"));
    }

    @Test
    @DisplayName("a token signed with another key is a JSON 401")
    void badlySignedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(foreignlySignedAccessToken("admin"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token is invalid"));
    }

    @Test
    @DisplayName("a token signed with an unknown key id is a JSON 401")
    void unknownKeyIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(unknownKeyIdAccessToken("admin"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token is invalid"));
    }

    @Test
    @DisplayName("an issued token is RS256 and names the signing key in its header")
    void issuedTokensAreRs256() throws Exception {
        String accessToken = accessToken(USER_USERNAME, USER_PASSWORD);
        String header = new String(java.util.Base64.getUrlDecoder()
                .decode(accessToken.substring(0, accessToken.indexOf('.'))),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"RS256\"");
        assertThat(header).contains(jwtKeyProvider.currentSigningKey().keyId());
    }

    @Test
    @DisplayName("refreshing rotates the token pair and burns the presented refresh token")
    void refreshRotatesTokens() throws Exception {
        String originalRefreshToken = refreshToken(USER_USERNAME, USER_PASSWORD);

        String rotated = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String newRefreshToken = JsonPath.read(rotated, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(originalRefreshToken);

        // The old token is revoked and can no longer be replayed.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid, expired or already used"));
    }

    @Test
    @DisplayName("an unknown refresh token is a 401")
    void refreshRejectsUnknownToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"this-token-was-never-issued\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("after logout the access token is blacklisted and the refresh token is revoked")
    void logoutInvalidatesBothTokens() throws Exception {
        String loginBody = login(USER_USERNAME, USER_PASSWORD);
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout requires authentication")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a supplied correlation id is echoed back; otherwise one is generated")
    void correlationIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .header(CorrelationIdFilter.HEADER_NAME, "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "trace-abc-123"));

        String generated = mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generated).isNotBlank();
    }

    @Test
    @DisplayName("the correlation id of a failed request appears in the error body")
    void correlationIdAppearsInErrorBody() throws Exception {
        mockMvc.perform(get("/api/reservations/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .header(CorrelationIdFilter.HEADER_NAME, "trace-error-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value("trace-error-1"))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "trace-error-1"));
    }
}
