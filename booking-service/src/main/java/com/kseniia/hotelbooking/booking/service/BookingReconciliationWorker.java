package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingReconciliationWorker {

    private final BookingRepository bookingRepository;
    private final HotelClient hotelClient;

    public BookingReconciliationWorker(BookingRepository bookingRepository, HotelClient hotelClient) {
        this.bookingRepository = bookingRepository;
        this.hotelClient = hotelClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseAndCancelPending(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (booking.getStatus() != BookingStatus.PENDING) {
            return false;
        }

        hotelClient.release(booking.getRoomId(), bookingId, "");
        booking.cancel();
        return true;
    }
}
