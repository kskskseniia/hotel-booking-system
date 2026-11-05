package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.AdminRoomBookingResponse;
import com.kseniia.hotelbooking.booking.dto.BookingResponse;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
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
    private final UserRepository userRepository;

    public BookingStateService(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingResponse createPending(Long userId, Long roomId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                roomId,
                startDate,
                endDate,
                BookingStatus.PENDING,
                LocalDateTime.now()
        ));
        return toResponse(booking);
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
    public void cancel(Long bookingId) {
        findForUpdate(bookingId).cancel();
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
}
