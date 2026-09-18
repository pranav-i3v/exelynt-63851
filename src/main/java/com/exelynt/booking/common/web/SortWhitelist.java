package com.exelynt.booking.common.web;

import com.exelynt.booking.common.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Translates a {@code sort=field,direction} query parameter into a {@link Sort},
 * accepting only explicitly allowed properties so the parameter cannot be used
 * to sort by - or probe - arbitrary columns.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    public static Sort resolve(String sortParameter, Set<String> allowedProperties, Sort fallback) {
        if (sortParameter == null || sortParameter.isBlank()) {
            return fallback;
        }
        String[] parts = sortParameter.split(",");
        String property = parts[0].trim();
        if (!allowedProperties.contains(property)) {
            throw new BadRequestException("Sorting by '" + property + "' is not supported; allowed properties are "
                    + String.join(", ", allowedProperties.stream().sorted().toList()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String rawDirection = parts[1].trim();
            direction = Sort.Direction.fromOptionalString(rawDirection)
                    .orElseThrow(() -> new BadRequestException(
                            "Sort direction '" + rawDirection + "' is not valid; use 'asc' or 'desc'"));
        }
        if (parts.length > 2) {
            throw new BadRequestException("sort must be expressed as 'property,direction'");
        }
        return Sort.by(direction, property);
    }
}
