package com.exelynt.booking.reservation.dto;

import com.exelynt.booking.common.validation.PeriodValidatable;
import com.exelynt.booking.common.validation.ValidPeriod;
import com.exelynt.booking.reservation.ReservationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Full update payload, ADMIN only. The owner of a reservation is never reassigned. */
@ValidPeriod
public record ReservationUpdateRequest(
        @NotNull(message = "resourceId is required")
        Long resourceId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime,

        @NotNull(message = "status is required and must be one of PENDING, CONFIRMED, CANCELLED")
        ReservationStatus status,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must be zero or greater")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 decimal digits")
        BigDecimal price) implements PeriodValidatable {
}
