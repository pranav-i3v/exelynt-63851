package com.exelynt.booking.common.validation;

import java.math.BigDecimal;

/** Implemented by filter DTOs that carry a min/max price range. */
public interface PriceRangeValidatable {

    BigDecimal minPrice();

    BigDecimal maxPrice();
}
