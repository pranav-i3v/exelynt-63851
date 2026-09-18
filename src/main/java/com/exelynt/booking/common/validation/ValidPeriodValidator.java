package com.exelynt.booking.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPeriodValidator implements ConstraintValidator<ValidPeriod, PeriodValidatable> {

    @Override
    public boolean isValid(PeriodValidatable value, ConstraintValidatorContext context) {
        if (value == null || value.startTime() == null || value.endTime() == null) {
            // Null-ness is reported by the field level @NotNull constraints.
            return true;
        }
        if (value.endTime().isAfter(value.startTime())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endTime")
                .addConstraintViolation();
        return false;
    }
}
