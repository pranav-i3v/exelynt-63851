package com.exelynt.booking.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level constraint asserting {@code minPrice <= maxPrice}. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPriceRangeValidator.class)
public @interface ValidPriceRange {

    String message() default "minPrice must be less than or equal to maxPrice";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
