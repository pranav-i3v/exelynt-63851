package com.exelynt.booking.reservation.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.exelynt.booking.reservation.common.ReservationStatus;
import com.exelynt.booking.reservation.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /**
     * Half-open overlap test: two windows clash when start < otherEnd and end > otherStart.
     *
     * <p>{@code excludedId} is null on create and carries the reservation's own id
     * on update, so a booking is not treated as clashing with itself. One query
     * rather than two near-identical ones, which would be easy to let diverge.</p>
     */
    @Query("""
            select count(r) from Reservation r
            where r.resource.id = :resourceId
              and r.status <> :cancelled
              and r.startTime < :endTime
              and r.endTime > :startTime
              and (:excludedId is null or r.id <> :excludedId)
            """)
    long countOverlapping(@Param("resourceId") Long resourceId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("cancelled") ReservationStatus cancelled,
                          @Param("excludedId") Long excludedId);

    /**
     * Loads the resource and the owner alongside the reservation.
     *
     * <p>Both are {@code LAZY} on the entity, and every response maps the resource
     * name and the owner's username, so without this each row costs two extra
     * queries — 202 statements for a full page of 100. Fetching them here also
     * means the ownership check in the service reads an already-loaded field
     * rather than depending on an open session behind a proxy.</p>
     */
    @Override
    @EntityGraph(attributePaths = {"resource", "user"})
    Page<Reservation> findAll(Specification<Reservation> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"resource", "user"})
    Optional<Reservation> findById(Long id);
}
