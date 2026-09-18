package com.exelynt.booking.reservation.dto;

import com.exelynt.booking.common.validation.PriceRangeValidatable;
import com.exelynt.booking.common.validation.ValidPriceRange;
import com.exelynt.booking.reservation.ReservationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Query parameters of {@code GET /api/reservations}. Bound from the query
 * string, so every default lives in the canonical constructor.
 */
@ValidPriceRange
public record ReservationSearchRequest(
        ReservationStatus status,

        @DecimalMin(value = "0.00", message = "minPrice must be zero or greater")
        @Digits(integer = 8, fraction = 2, message = "minPrice must have at most 8 integer and 2 decimal digits")
        BigDecimal minPrice,

        @DecimalMin(value = "0.00", message = "maxPrice must be zero or greater")
        @Digits(integer = 8, fraction = 2, message = "maxPrice must have at most 8 integer and 2 decimal digits")
        BigDecimal maxPrice,

        @Min(value = 0, message = "page must be zero or greater")
        Integer page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must be at most 100")
        Integer size,

        String sort) implements PriceRangeValidatable {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT = "id,desc";

    public ReservationSearchRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sort = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
    }
}
