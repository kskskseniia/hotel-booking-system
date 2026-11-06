package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.exception.UserDeletionConflictException;
import com.kseniia.hotelbooking.booking.exception.UserNotFoundException;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserDeletionWorker {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final UserLockService userLockService;
    private final HotelClient hotelClient;

    public UserDeletionWorker(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            UserLockService userLockService,
            HotelClient hotelClient
    ) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.userLockService = userLockService;
        this.hotelClient = hotelClient;
    }

    @Transactional
    public Long prepare(String username) {
        User user = userLockService.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);
        List<Booking> bookings = bookingRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId());
        if (bookings.stream().anyMatch(booking -> booking.getStatus() == BookingStatus.PENDING)) {
            throw new UserDeletionConflictException();
        }
        user.requestDeletion();
        return user.getId();
    }

    @Transactional(readOnly = true)
    public List<Long> findBookingIds(Long userId) {
        return bookingRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(Booking::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseBooking(Long userId, Long bookingId, String authorization) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !booking.getUser().getId().equals(userId)) {
            return;
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new UserDeletionConflictException();
        }

        if (booking.getRoomId() != null) {
            hotelClient.release(booking.getRoomId(), bookingId, authorization);
        }
        booking.cancel();
        bookingRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeDeletion(Long userId) {
        User user = userLockService.findById(userId).orElse(null);
        if (user == null) {
            return true;
        }
        if (!user.isDeletionRequested()) {
            return false;
        }

        List<Booking> bookings = bookingRepository.findAllByUser_IdOrderByCreatedAtDesc(userId);
        if (bookings.stream().anyMatch(booking -> booking.getStatus() != BookingStatus.CANCELLED)) {
            return false;
        }

        bookingRepository.deleteAll(bookings);
        bookingRepository.flush();
        userRepository.delete(user);
        userRepository.flush();
        return true;
    }
}
