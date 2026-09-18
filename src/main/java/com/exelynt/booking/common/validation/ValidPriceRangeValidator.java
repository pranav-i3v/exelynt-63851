package com.exelynt.booking.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPriceRangeValidator implements ConstraintValidator<ValidPriceRange, PriceRangeValidatable> {

    @Override
    public boolean isValid(PriceRangeValidatable value, ConstraintValidatorContext context) {
        if (value == null || value.minPrice() == null || value.maxPrice() == null) {
            return true;
        }
        if (value.minPrice().compareTo(value.maxPrice()) <= 0) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("minPrice")
                .addConstraintViolation();
        return false;
    }
}
