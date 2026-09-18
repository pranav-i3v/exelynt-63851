package com.exelynt.booking.reservation.repository;

import java.time.LocalDateTime;

import com.exelynt.booking.reservation.common.ReservationStatus;
import com.exelynt.booking.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /** Half-open overlap test: two windows clash when start < otherEnd and end > otherStart. */
    @Query("""
            select count(r) from Reservation r
            where r.resource.id = :resourceId
              and r.status <> :cancelled
              and r.startTime < :endTime
              and r.endTime > :startTime
            """)
    long countOverlapping(@Param("resourceId") Long resourceId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("cancelled") ReservationStatus cancelled);

    @Query("""
            select count(r) from Reservation r
            where r.resource.id = :resourceId
              and r.id <> :excludedId
              and r.status <> :cancelled
              and r.startTime < :endTime
              and r.endTime > :startTime
            """)
    long countOverlappingExcluding(@Param("resourceId") Long resourceId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("cancelled") ReservationStatus cancelled,
                                   @Param("excludedId") Long excludedId);
}
