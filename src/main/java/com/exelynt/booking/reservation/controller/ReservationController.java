package com.exelynt.booking.reservation.controller;

import com.exelynt.booking.common.model.PageResponse;
import com.exelynt.booking.reservation.service.ReservationService;
import com.exelynt.booking.reservation.dto.ReservationCreateRequest;
import com.exelynt.booking.reservation.dto.ReservationResponse;
import com.exelynt.booking.reservation.dto.ReservationSearchRequest;
import com.exelynt.booking.reservation.dto.ReservationUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "A USER manages only their own reservations; an ADMIN manages all of them")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Create a reservation (ADMIN, USER). The owner is taken from the token, "
            + "so any userId in the body is ignored")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No such resource"),
            @ApiResponse(responseCode = "409", description = "Resource inactive or already booked for that period")
    })
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest request) {
        ReservationResponse created = reservationService.create(request);
        return ResponseEntity.created(URI.create("/api/reservations/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Search reservations with filtering, paging and whitelisted sorting. "
            + "An ADMIN sees everything, a USER only their own")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Invalid filter, page, size or sort"))
    public ResponseEntity<PageResponse<ReservationResponse>> search(
            @Valid @ModelAttribute ReservationSearchRequest request) {
        return ResponseEntity.ok(reservationService.search(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one reservation (ADMIN any, USER only their own)")
    @ApiResponses(@ApiResponse(responseCode = "404",
            description = "No such reservation, or it belongs to somebody else"))
    public ResponseEntity<ReservationResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a reservation (ADMIN)")
    public ResponseEntity<ReservationResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody ReservationUpdateRequest request) {
        return ResponseEntity.ok(reservationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a reservation (ADMIN)")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Deleted"))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
