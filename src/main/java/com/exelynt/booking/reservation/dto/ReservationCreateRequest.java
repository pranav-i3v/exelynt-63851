package com.exelynt.booking.reservation.dto;

import com.exelynt.booking.common.validation.PeriodValidatable;
import com.exelynt.booking.common.validation.ValidPeriod;
import com.exelynt.booking.reservation.common.ReservationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Create payload.
 *
 * <p>There is deliberately no {@code userId} field: the owner is always the
 * authenticated caller. A {@code userId} sent by a client is simply not bound
 * and therefore ignored.</p>
 *
 * <p><strong>{@code status} is only honoured for an ADMIN.</strong> A booking
 * created by a USER is always PENDING, whatever the body asks for — approving
 * your own request is not something the requester gets to do. The service
 * enforces this; do not move the decision into {@link #statusOrDefault()},
 * which knows nothing about who is calling.</p>
 */
@ValidPeriod
public record ReservationCreateRequest(
        @NotNull(message = "resourceId is required")
        Long resourceId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must be zero or greater")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 decimal digits")
        BigDecimal price,

        ReservationStatus status) implements PeriodValidatable {

    /**
     * The requested status, defaulting to PENDING. Only meaningful for an ADMIN:
     * the service overrides it with PENDING for everybody else.
     */
    public ReservationStatus statusOrDefault() {
        return status == null ? ReservationStatus.PENDING : status;
    }
}
