package com.exelynt.booking.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level constraint asserting {@code endTime} is strictly after {@code startTime}. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPeriodValidator.class)
public @interface ValidPeriod {

    String message() default "endTime must be after startTime";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
