package com.exelynt.booking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HealthEndpointTest extends IntegrationTestSupport {

    @Test
    @DisplayName("health, liveness and readiness are public")
    void healthEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("details are hidden from anonymous callers and shown to an ADMIN")
    void detailsAreVisibleOnlyToAdmin() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("readiness includes the database check")
    void readinessIncludesDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.readinessState").exists());
    }

    @Test
    @DisplayName("endpoints outside health and info are not exposed")
    void otherEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNotFound());
    }
}
