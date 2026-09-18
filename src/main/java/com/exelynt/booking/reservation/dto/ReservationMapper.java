package com.exelynt.booking.reservation.dto;

import com.exelynt.booking.reservation.Reservation;

/** Entity to DTO translation; entities never cross the API boundary. */
public final class ReservationMapper {

    private ReservationMapper() {
    }

    public static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getResource().getName(),
                reservation.getUser().getId(),
                reservation.getUser().getUsername(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getPrice(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                reservation.getCreatedBy(),
                reservation.getUpdatedBy());
    }
}
