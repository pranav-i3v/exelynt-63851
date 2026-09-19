package com.exelynt.booking.reservation.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.exelynt.booking.reservation.entity.Reservation;
import org.springframework.data.jpa.domain.Specification;

/** Composable predicates behind {@code GET /api/reservations}. */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<Reservation> ownedBy(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Reservation> hasStatus(ReservationStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Reservation> priceAtLeast(BigDecimal minPrice) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Reservation> priceAtMost(BigDecimal maxPrice) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    /**
     * Builds the effective query. {@code restrictToUserId} is non-null for a USER,
     * which is what keeps one user's reservations invisible to another.
     */
    public static Specification<Reservation> build(Long restrictToUserId,
                                                   ReservationStatus status,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice) {
        List<Specification<Reservation>> specifications = new ArrayList<>();
        if (restrictToUserId != null) {
            specifications.add(ownedBy(restrictToUserId));
        }
        if (status != null) {
            specifications.add(hasStatus(status));
        }
        if (minPrice != null) {
            specifications.add(priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            specifications.add(priceAtMost(maxPrice));
        }
        if (specifications.isEmpty()) {
            // An ADMIN with no filters matches everything. allOf over an empty list
            // is not a documented always-true, so say so explicitly instead.
            return (root, query, builder) -> builder.conjunction();
        }
        // Specification.allOf (Spring Data JPA 3.1+) ANDs the parts together; each
        // predicate above ignores the parameters it does not need, which is why
        // some lambdas leave query and builder unused.
        return Specification.allOf(specifications);
    }
}
