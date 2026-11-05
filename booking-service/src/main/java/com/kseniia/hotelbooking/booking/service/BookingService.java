package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.client.RoomCandidate;
import com.kseniia.hotelbooking.booking.dto.AdminRoomBookingResponse;
import com.kseniia.hotelbooking.booking.dto.BookingResponse;
import com.kseniia.hotelbooking.booking.dto.CreateBookingRequest;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingService.class);

    private final BookingStateService bookingStateService;
    private final HotelClient hotelClient;

    public BookingService(BookingStateService bookingStateService, HotelClient hotelClient) {
        this.bookingStateService = bookingStateService;
        this.hotelClient = hotelClient;
    }

    public BookingResponse create(
            CreateBookingRequest request,
            Long userId,
            String authorization
    ) {
        validate(request);
        Long roomId = request.autoSelect()
                ? selectRecommendedRoom(authorization)
                : request.roomId();
        BookingResponse pending = bookingStateService.createPending(
                userId,
                roomId,
                request.startDate(),
                request.endDate()
        );

        try {
            hotelClient.confirmAvailability(
                    roomId,
                    pending.id(),
                    request.startDate(),
                    request.endDate(),
                    authorization
            );
            BookingResponse confirmed = bookingStateService.confirm(pending.id());
            LOGGER.info("Booking {} transitioned PENDING -> CONFIRMED", pending.id());
            return confirmed;
        } catch (RuntimeException exception) {
            compensate(roomId, pending.id(), authorization);
            bookingStateService.cancel(pending.id());
            LOGGER.warn("Booking {} transitioned PENDING -> CANCELLED", pending.id());
            throw exception;
        }
    }

    public List<BookingResponse> getHistory(Long userId) {
        return bookingStateService.getHistory(userId);
    }

    public List<AdminRoomBookingResponse> getRoomBookings(Long roomId) {
        return bookingStateService.getRoomBookings(roomId);
    }

    private void validate(CreateBookingRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
        if (!request.autoSelect() && request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required when autoSelect is false");
        }
    }

    private Long selectRecommendedRoom(String authorization) {
        return hotelClient.getRecommendedRooms(authorization).stream()
                .map(RoomCandidate::id)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(RoomUnavailableException::new);
    }

    private void compensate(Long roomId, Long bookingId, String authorization) {
        try {
            hotelClient.release(roomId, bookingId, authorization);
            LOGGER.info("Released room hold for booking {}", bookingId);
        } catch (RuntimeException compensationFailure) {
            LOGGER.error("Failed to release room hold for booking {}", bookingId, compensationFailure);
        }
    }
}
