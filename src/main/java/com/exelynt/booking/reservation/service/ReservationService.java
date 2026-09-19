package com.exelynt.booking.reservation.service;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.common.exception.type.ConflictException;
import com.exelynt.booking.common.exception.type.NotFoundException;
import com.exelynt.booking.common.model.PageResponse;
import com.exelynt.booking.common.web.SortWhitelist;
import com.exelynt.booking.reservation.repository.ReservationRepository;
import com.exelynt.booking.reservation.common.ReservationSpecifications;
import com.exelynt.booking.reservation.common.ReservationStatus;
import com.exelynt.booking.reservation.dto.ReservationCreateRequest;
import com.exelynt.booking.reservation.dto.ReservationMapper;
import com.exelynt.booking.reservation.dto.ReservationResponse;
import com.exelynt.booking.reservation.dto.ReservationSearchRequest;
import com.exelynt.booking.reservation.dto.ReservationUpdateRequest;
import com.exelynt.booking.reservation.entity.Reservation;
import com.exelynt.booking.resource.entity.Resource;
import com.exelynt.booking.resource.service.ResourceService;
import com.exelynt.booking.security.user.AppUserPrincipal;
import com.exelynt.booking.security.common.SecurityUtils;
import com.exelynt.booking.user.entity.User;
import com.exelynt.booking.user.service.UserService;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final String ENTITY_TYPE = "Reservation";

    /** Only these properties may be used in {@code sort}. */
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "startTime", "endTime", "price", "status", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    private final ReservationRepository reservationRepository;
    private final ResourceService resourceService;
    private final UserService userService;
    private final AuditService auditService;

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceService resourceService,
                              UserService userService,
                              AuditService auditService) {
        this.reservationRepository = reservationRepository;
        this.resourceService = resourceService;
        this.userService = userService;
        this.auditService = auditService;
    }

    /** The owner comes from the security context, never from the payload. */
    @Transactional
    public ReservationResponse create(ReservationCreateRequest request) {
        AppUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        Resource resource = resourceService.getEntityOrThrow(request.resourceId());
        requireActive(resource);
        User owner = userService.getById(principal.getUserId());

        // Confirming is an ADMIN decision. A USER asking for CONFIRMED gets PENDING
        // rather than a 403: the booking is still created, it just is not approved
        // by the person who requested it.
        ReservationStatus status = principal.isAdmin() ? request.statusOrDefault() : ReservationStatus.PENDING;
        requireFreeSlot(resource, request.startTime(), request.endTime(), status, null);

        Reservation saved = reservationRepository.save(new Reservation(
                resource, owner, request.startTime(), request.endTime(), status, request.price()));
        auditService.record(principal.getUsername(), AuditAction.RESERVATION_CREATED, ENTITY_TYPE, saved.getId());
        return ReservationMapper.toResponse(saved);
    }

    /** ADMIN sees every reservation; a USER is silently restricted to their own. */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> search(ReservationSearchRequest request) {
        AppUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        Long restrictToUserId = principal.isAdmin() ? null : principal.getUserId();

        Sort sort = SortWhitelist.resolve(request.sort(), SORTABLE_PROPERTIES, DEFAULT_SORT);
        Pageable pageable = PageRequest.of(request.page(), request.size(), sort);
        Specification<Reservation> specification = ReservationSpecifications.build(
                restrictToUserId, request.status(), request.minPrice(), request.maxPrice());

        Page<ReservationResponse> page = reservationRepository.findAll(specification, pageable)
                .map(ReservationMapper::toResponse);
        return PageResponse.from(page);
    }

    /**
     * A USER asking for someone else's reservation gets the same 404 as for a
     * reservation that does not exist, so the API never confirms its existence.
     */
    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        AppUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        Reservation reservation = getOrThrow(id);
        if (!principal.isAdmin() && !reservation.isOwnedBy(principal.getUserId())) {
            throw NotFoundException.of(ENTITY_TYPE, id);
        }
        return ReservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse update(Long id, ReservationUpdateRequest request) {
        Reservation reservation = getOrThrow(id);
        Resource resource = resourceService.getEntityOrThrow(request.resourceId());
        // Same invariant as on create: a reservation cannot be moved onto a
        // resource that has been taken out of service.
        requireActive(resource);
        requireFreeSlot(resource, request.startTime(), request.endTime(), request.status(), id);

        ReservationStatus previousStatus = reservation.getStatus();
        reservation.setResource(resource);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(request.status());
        reservation.setPrice(request.price());
        Reservation saved = reservationRepository.save(reservation);

        String actor = SecurityUtils.currentUsername().orElse(null);
        auditService.record(actor, AuditAction.RESERVATION_UPDATED, ENTITY_TYPE, id);
        if (previousStatus != request.status()) {
            auditService.record(actor, AuditAction.RESERVATION_STATUS_CHANGED, ENTITY_TYPE, id);
        }
        return ReservationMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Reservation reservation = getOrThrow(id);
        reservationRepository.delete(reservation);
        auditService.record(SecurityUtils.currentUsername().orElse(null),
                AuditAction.RESERVATION_DELETED, ENTITY_TYPE, id);
    }

    private void requireActive(Resource resource) {
        if (!resource.isActive()) {
            throw new ConflictException("Resource " + resource.getId() + " is not active and cannot be booked");
        }
    }

    /**
     * A PENDING booking holds the slot just as a CONFIRMED one does; only
     * CANCELLED frees it. Letting several PENDING bookings pile up on the same
     * window would just defer the clash to whoever approves them.
     *
     * @param excludedId the reservation being updated, so it does not clash with itself
     */
    private void requireFreeSlot(Resource resource,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime,
                                 ReservationStatus status,
                                 Long excludedId) {
        if (status == ReservationStatus.CANCELLED) {
            return;
        }
        long overlapping = reservationRepository.countOverlapping(
                resource.getId(), startTime, endTime, ReservationStatus.CANCELLED, excludedId);
        if (overlapping > 0) {
            throw new ConflictException("Resource " + resource.getId()
                    + " is already booked for the requested period");
        }
    }

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY_TYPE, id));
    }
}
