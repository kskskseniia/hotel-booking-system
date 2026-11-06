package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.AdminRoomBookingResponse;
import com.kseniia.hotelbooking.booking.dto.BookingResponse;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingRequestStatus;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingStateService {

    private final BookingRepository bookingRepository;
    private final UserLockService userLockService;

    public BookingStateService(BookingRepository bookingRepository, UserLockService userLockService) {
        this.bookingRepository = bookingRepository;
        this.userLockService = userLockService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PendingBookingResult createPending(
            Long userId,
            String requestId,
            boolean autoSelect,
            Long requestedRoomId,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User user = userLockService.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isDeletionRequested()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        Booking existing = bookingRepository.findByUser_IdAndRequestId(userId, requestId).orElse(null);
        if (existing != null) {
            validateRequest(existing, autoSelect, requestedRoomId, startDate, endDate);
            return new PendingBookingResult(toResponse(existing), existing.getRequestStatus(), false);
        }
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                requestId,
                autoSelect,
                requestedRoomId,
                roomId,
                startDate,
                endDate,
                BookingStatus.PENDING,
                LocalDateTime.now()
        ));
        return new PendingBookingResult(toResponse(booking), booking.getRequestStatus(), true);
    }

    @Transactional(readOnly = true)
    public PendingBookingResult findByRequest(
            Long userId,
            String requestId,
            boolean autoSelect,
            Long requestedRoomId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Booking booking = bookingRepository.findByUser_IdAndRequestId(userId, requestId).orElse(null);
        if (booking == null) {
            return null;
        }
        validateRequest(booking, autoSelect, requestedRoomId, startDate, endDate);
        return new PendingBookingResult(toResponse(booking), booking.getRequestStatus(), false);
    }

    @Transactional(readOnly = true)
    public PendingBookingResult findByRequestId(Long userId, String requestId) {
        return bookingRepository.findByUser_IdAndRequestId(userId, requestId)
                .map(booking -> new PendingBookingResult(
                        toResponse(booking),
                        booking.getRequestStatus(),
                        false
                ))
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingResponse confirm(Long bookingId) {
        Booking booking = findForUpdate(bookingId);
        booking.confirm();
        return toResponse(booking);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingResponse assignRoom(Long bookingId, Long roomId) {
        Booking booking = findForUpdate(bookingId);
        booking.assignRoom(roomId);
        return toResponse(booking);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRequest(Long bookingId, BookingRequestStatus requestStatus, boolean cancelBooking) {
        findForUpdate(bookingId).failRequest(requestStatus, cancelBooking);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Long bookingId, Long userId) {
        Booking booking = findForUpdate(bookingId);
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        booking.cancel();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getHistory(Long userId) {
        return bookingRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUser_Id(bookingId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<AdminRoomBookingResponse> getRoomBookings(Long roomId) {
        return bookingRepository.findAllByRoomIdOrderByStartDateAscIdAsc(roomId).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    private Booking findForUpdate(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void validateRequest(
            Booking booking,
            boolean autoSelect,
            Long requestedRoomId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (!booking.matchesRequest(autoSelect, requestedRoomId, startDate, endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "requestId was already used with a different booking request"
            );
        }
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoomId(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }

    private AdminRoomBookingResponse toAdminResponse(Booking booking) {
        return new AdminRoomBookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getRoomId(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }

    public record PendingBookingResult(
            BookingResponse booking,
            BookingRequestStatus requestStatus,
            boolean created
    ) {
    }
}
