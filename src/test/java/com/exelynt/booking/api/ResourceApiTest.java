package com.exelynt.booking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ResourceApiTest extends IntegrationTestSupport {

    @Test
    @DisplayName("both roles may read resources")
    void bothRolesCanRead() throws Exception {
        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/resources/1").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Meeting Room Alpha"))
                .andExpect(jsonPath("$.type").value("ROOM"))
                .andExpect(jsonPath("$.createdBy").value("system"));
    }

    @Test
    @DisplayName("a USER is forbidden from creating, updating and deleting resources")
    void userIsForbiddenFromWriting() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rogue Room\",\"type\":\"ROOM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(put("/api/resources/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"type\":\"ROOM\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/resources/1").header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ADMIN can create, update and delete a resource")
    void adminManagesResources() throws Exception {
        String created = mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lab Bench\",\"type\":\"EQUIPMENT\",\"description\":\"Bench\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lab Bench"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdBy").value("admin"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/resources/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lab Bench 2\",\"type\":\"EQUIPMENT\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lab Bench 2"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/resources/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resources/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a duplicate resource name is a 409")
    void duplicateNameIsConflict() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meeting Room Alpha\",\"type\":\"ROOM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("an invalid resource type is a 400")
    void invalidTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Odd\",\"type\":\"SPACESHIP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a missing resource is a 404")
    void unknownResourceIsNotFound() throws Exception {
        mockMvc.perform(get("/api/resources/999999").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
