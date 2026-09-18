package com.exelynt.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.common.exception.type.BadRequestException;
import com.exelynt.booking.common.exception.type.ConflictException;
import com.exelynt.booking.common.exception.type.NotFoundException;
import com.exelynt.booking.common.model.PageResponse;
import com.exelynt.booking.reservation.common.ReservationStatus;
import com.exelynt.booking.reservation.dto.ReservationCreateRequest;
import com.exelynt.booking.reservation.dto.ReservationResponse;
import com.exelynt.booking.reservation.dto.ReservationSearchRequest;
import com.exelynt.booking.reservation.entity.Reservation;
import com.exelynt.booking.reservation.repository.ReservationRepository;
import com.exelynt.booking.reservation.service.ReservationService;
import com.exelynt.booking.resource.entity.Resource;
import com.exelynt.booking.resource.service.ResourceService;
import com.exelynt.booking.resource.common.ResourceType;
import com.exelynt.booking.security.user.AppUserPrincipal;
import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import com.exelynt.booking.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 1, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2030, 1, 1, 10, 0);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ResourceService resourceService;
    @Mock
    private UserService userService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReservationService reservationService;

    private User alice;
    private User bob;
    private Resource room;

    @BeforeEach
    void setUp() {
        alice = new User("alice", "$2a$10$hash", Role.USER);
        ReflectionTestUtils.setField(alice, "id", 7L);
        bob = new User("bob", "$2a$10$hash", Role.USER);
        ReflectionTestUtils.setField(bob, "id", 8L);
        room = new Resource("Room A", ResourceType.ROOM, "desc", true);
        ReflectionTestUtils.setField(room, "id", 3L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the owner is taken from the security context, never from the payload")
    void ownerComesFromSecurityContext() {
        authenticateAs(alice, Role.USER);
        when(resourceService.getEntityOrThrow(3L)).thenReturn(room);
        when(userService.getById(7L)).thenReturn(alice);
        when(reservationRepository.countOverlapping(any(), any(), any(), any())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 1L);
            return reservation;
        });

        ReservationResponse response = reservationService.create(new ReservationCreateRequest(
                3L, START, END, new BigDecimal("100.50"), null));

        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(alice);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.username()).isEqualTo("alice");
        verify(auditService).record("alice", AuditAction.RESERVATION_CREATED, "Reservation", 1L);
    }

    @Test
    @DisplayName("an overlapping booking on the same resource is a 409")
    void rejectsOverlappingBooking() {
        authenticateAs(alice, Role.USER);
        when(resourceService.getEntityOrThrow(3L)).thenReturn(room);
        when(userService.getById(7L)).thenReturn(alice);
        when(reservationRepository.countOverlapping(3L, START, END, ReservationStatus.CANCELLED)).thenReturn(1L);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationCreateRequest(3L, START, END, new BigDecimal("10.00"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already booked");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("an inactive resource cannot be booked")
    void rejectsInactiveResource() {
        authenticateAs(alice, Role.USER);
        Resource inactive = new Resource("Retired", ResourceType.ROOM, null, false);
        ReflectionTestUtils.setField(inactive, "id", 4L);
        when(resourceService.getEntityOrThrow(4L)).thenReturn(inactive);

        assertThatThrownBy(() -> reservationService.create(
                new ReservationCreateRequest(4L, START, END, new BigDecimal("10.00"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("a USER asking for another user's reservation gets 404, not 403")
    void hidesOtherUsersReservation() {
        authenticateAs(alice, Role.USER);
        when(reservationRepository.findById(9L)).thenReturn(Optional.of(reservationOf(bob, 9L)));

        assertThatThrownBy(() -> reservationService.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Reservation 9");
    }

    @Test
    @DisplayName("an ADMIN may read anybody's reservation")
    void adminReadsAnyReservation() {
        authenticateAs(alice, Role.ADMIN);
        when(reservationRepository.findById(9L)).thenReturn(Optional.of(reservationOf(bob, 9L)));

        assertThat(reservationService.findById(9L).username()).isEqualTo("bob");
    }

    @Test
    @DisplayName("a USER search is restricted to their own rows and defaults to id,desc")
    void searchRestrictsUserAndAppliesDefaultSort() {
        authenticateAs(alice, Role.USER);
        when(reservationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reservationOf(alice, 1L))));

        PageResponse<ReservationResponse> page = reservationService.search(
                new ReservationSearchRequest(null, null, null, null, null, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Specification> specification = ArgumentCaptor.forClass(Specification.class);
        verify(reservationRepository).findAll(specification.capture(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(specification.getValue()).isNotNull();
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("sorting by a property outside the whitelist is a 400")
    void rejectsNonWhitelistedSort() {
        authenticateAs(alice, Role.USER);

        assertThatThrownBy(() -> reservationService.search(
                new ReservationSearchRequest(null, null, null, null, null, "password,asc")))
                .isInstanceOf(BadRequestException.class);

        verify(reservationRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("deleting an unknown reservation is a 404")
    void deleteRejectsUnknownId() {
        when(reservationRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.delete(123L)).isInstanceOf(NotFoundException.class);
    }

    private Reservation reservationOf(User owner, Long id) {
        Reservation reservation = new Reservation(
                room, owner, START, END, ReservationStatus.PENDING, new BigDecimal("10.00"));
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private void authenticateAs(User user, Role role) {
        AppUserPrincipal principal = new AppUserPrincipal(
                user.getId(), user.getUsername(), user.getPassword(), role, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
