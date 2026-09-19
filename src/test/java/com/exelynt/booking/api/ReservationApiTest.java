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
class ReservationApiTest extends IntegrationTestSupport {

    private static final String USER_BOOKING = """
            {"resourceId":1,"startTime":"2030-01-01T09:00:00","endTime":"2030-01-01T10:00:00","price":100.50}
            """;

    @Test
    @DisplayName("a USER can book and then read back their own reservation")
    void userCreatesAndReadsOwnReservation() throws Exception {
        String created = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER_BOOKING))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(100.50))
                .andExpect(jsonPath("$.resourceName").value("Meeting Room Alpha"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("a userId in the request body is ignored: the owner comes from the token")
    void userIdInBodyIsIgnored() throws Exception {
        String adminId = JsonPath.read(mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":2,"startTime":"2031-05-05T08:00:00",
                                 "endTime":"2031-05-05T09:00:00","price":10.00}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.userId").toString();

        // The USER claims to be the admin; the reservation must still belong to "user".
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%s,"user":{"id":%s},"resourceId":1,
                                 "startTime":"2030-06-01T09:00:00","endTime":"2030-06-01T10:00:00","price":5.00}
                                """.formatted(adminId, adminId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.not(Integer.parseInt(adminId))));
    }

    @Test
    @DisplayName("a USER cannot confirm their own booking: CONFIRMED in the body becomes PENDING")
    void userCannotSelfConfirm() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2037-01-01T09:00:00",
                                 "endTime":"2037-01-01T10:00:00","price":10.00,"status":"CONFIRMED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("an ADMIN may create a booking already confirmed")
    void adminMayConfirmOnCreate() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2037-02-01T09:00:00",
                                 "endTime":"2037-02-01T10:00:00","price":10.00,"status":"CONFIRMED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("a reservation cannot be moved onto a deactivated resource")
    void updateRejectsInactiveResource() throws Exception {
        int id = createReservation(userAccessToken, 1, "2038-01-01T09:00:00", "2038-01-01T10:00:00", "10.00");
        // Take resource 2 out of service, then try to move the booking onto it.
        mockMvc.perform(put("/api/resources/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Company Van\",\"type\":\"VEHICLE\",\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":2,"startTime":"2038-01-01T09:00:00",
                                 "endTime":"2038-01-01T10:00:00","status":"CONFIRMED","price":10.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not active")));
    }

    @Test
    @DisplayName("an update does not clash with the reservation being updated")
    void updateDoesNotClashWithItself() throws Exception {
        int id = createReservation(adminAccessToken, 3, "2039-01-01T09:00:00", "2039-01-01T11:00:00", "10.00");

        // Same window, same reservation: the excluded-id path must not see a clash.
        mockMvc.perform(put("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":3,"startTime":"2039-01-01T09:00:00",
                                 "endTime":"2039-01-01T11:00:00","status":"CONFIRMED","price":25.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(25.00));
    }

    @Test
    @DisplayName("a USER cannot read somebody else's reservation and gets 404, not 403")
    void userCannotReadForeignReservation() throws Exception {
        String adminReservation = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":3,"startTime":"2032-01-01T09:00:00",
                                 "endTime":"2032-01-01T10:00:00","price":42.00}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(adminReservation, "$.id");

        mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a USER sees only their own reservations, an ADMIN sees all of them")
    void listingIsScopedByRole() throws Exception {
        createReservation(userAccessToken, 1, "2033-01-01T09:00:00", "2033-01-01T10:00:00", "10.00");
        createReservation(adminAccessToken, 2, "2033-01-01T09:00:00", "2033-01-01T10:00:00", "20.00");

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("user"))
                .andExpect(jsonPath("$.sort").value("id: DESC"));

        mockMvc.perform(get("/api/reservations").header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("filtering by status and price range, paging and sorting all work together")
    void filteringPagingAndSorting() throws Exception {
        createReservation(adminAccessToken, 1, "2034-01-01T09:00:00", "2034-01-01T10:00:00", "50.00");
        createReservation(adminAccessToken, 2, "2034-01-01T09:00:00", "2034-01-01T10:00:00", "150.00");
        createReservation(adminAccessToken, 3, "2034-01-01T09:00:00", "2034-01-01T10:00:00", "250.00");

        mockMvc.perform(get("/api/reservations?minPrice=100&maxPrice=200")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(150.00));

        mockMvc.perform(get("/api/reservations?status=PENDING&page=0&size=2&sort=price,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].price").value(50.00))
                .andExpect(jsonPath("$.content[1].price").value(150.00));

        mockMvc.perform(get("/api/reservations?page=1&size=2&sort=price,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].price").value(250.00));
    }

    @Test
    @DisplayName("invalid filters, paging and sorting are rejected with 400")
    void invalidQueryParametersAreRejected() throws Exception {
        mockMvc.perform(get("/api/reservations?minPrice=100&maxPrice=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"));

        mockMvc.perform(get("/api/reservations?page=-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reservations?size=101")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reservations?status=NOT_A_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reservations?sort=password,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not supported")));
    }

    @Test
    @DisplayName("body validation covers required fields, the period and the price scale")
    void bodyValidationIsEnforced() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2030-01-01T10:00:00",
                                 "endTime":"2030-01-01T09:00:00","price":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endTime"));

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2030-01-01T09:00:00",
                                 "endTime":"2030-01-01T10:00:00","price":-1.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"));

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2030-01-01T09:00:00",
                                 "endTime":"2030-01-01T10:00:00","price":10.123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
    }

    @Test
    @DisplayName("booking a resource twice for the same window is a 409")
    void overlappingBookingIsConflict() throws Exception {
        createReservation(userAccessToken, 1, "2035-01-01T09:00:00", "2035-01-01T11:00:00", "10.00");

        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"startTime":"2035-01-01T10:00:00",
                                 "endTime":"2035-01-01T12:00:00","price":10.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("booking an unknown resource is a 404")
    void unknownResourceIsNotFound() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":987654,"startTime":"2030-01-01T09:00:00",
                                 "endTime":"2030-01-01T10:00:00","price":10.00}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("only an ADMIN may update or delete a reservation")
    void onlyAdminCanUpdateOrDelete() throws Exception {
        int id = createReservation(userAccessToken, 1, "2036-01-01T09:00:00", "2036-01-01T10:00:00", "10.00");
        String update = """
                {"resourceId":1,"startTime":"2036-01-01T09:00:00","endTime":"2036-01-01T11:00:00",
                 "status":"CONFIRMED","price":99.99}
                """;

        mockMvc.perform(put("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/reservations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(99.99))
                // The owner is never reassigned by an update.
                .andExpect(jsonPath("$.username").value("user"));

        mockMvc.perform(delete("/api/reservations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());
    }

    private int createReservation(String token, int resourceId, String start, String end, String price)
            throws Exception {
        String body = """
                {"resourceId":%d,"startTime":"%s","endTime":"%s","price":%s}
                """.formatted(resourceId, start, end, price);
        String created = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }
}
