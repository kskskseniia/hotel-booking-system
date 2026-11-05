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
        List<Long> roomIds = request.autoSelect()
                ? recommendedRoomIds(authorization)
                : List.of(request.roomId());
        BookingResponse pending = bookingStateService.createPending(
                userId,
                roomIds.getFirst(),
                request.startDate(),
                request.endDate()
        );

        if (request.autoSelect()) {
            return confirmFirstAvailable(pending.id(), roomIds, request, authorization);
        }
        return confirmSelectedRoom(pending.id(), roomIds.getFirst(), request, authorization);
    }

    private BookingResponse confirmFirstAvailable(
            Long bookingId,
            List<Long> roomIds,
            CreateBookingRequest request,
            String authorization
    ) {
        for (Long roomId : roomIds) {
            bookingStateService.assignRoom(bookingId, roomId);
            try {
                return confirm(bookingId, roomId, request, authorization);
            } catch (RoomUnavailableException exception) {
                LOGGER.info("Room {} is unavailable for booking {}, trying next candidate", roomId, bookingId);
            } catch (RuntimeException exception) {
                handleFailure(roomId, bookingId, authorization);
                throw exception;
            }
        }
        bookingStateService.cancel(bookingId);
        throw new RoomUnavailableException();
    }

    private BookingResponse confirmSelectedRoom(
            Long bookingId,
            Long roomId,
            CreateBookingRequest request,
            String authorization
    ) {
        try {
            return confirm(bookingId, roomId, request, authorization);
        } catch (RuntimeException exception) {
            handleFailure(roomId, bookingId, authorization);
            throw exception;
        }
    }

    private BookingResponse confirm(
            Long bookingId,
            Long roomId,
            CreateBookingRequest request,
            String authorization
    ) {
        hotelClient.confirmAvailability(
                roomId,
                bookingId,
                request.startDate(),
                request.endDate(),
                authorization
        );
        BookingResponse confirmed = bookingStateService.confirm(bookingId);
        LOGGER.info("Booking {} transitioned PENDING -> CONFIRMED", bookingId);
        return confirmed;
    }

    private void handleFailure(Long roomId, Long bookingId, String authorization) {
        if (compensate(roomId, bookingId, authorization)) {
            bookingStateService.cancel(bookingId);
            LOGGER.warn("Booking {} transitioned PENDING -> CANCELLED", bookingId);
        } else {
            LOGGER.error("Booking {} remains PENDING for compensation retry", bookingId);
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

    private List<Long> recommendedRoomIds(String authorization) {
        List<Long> roomIds = hotelClient.getRecommendedRooms(authorization).stream()
                .map(RoomCandidate::id)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (roomIds.isEmpty()) {
            throw new RoomUnavailableException();
        }
        return roomIds;
    }

    private boolean compensate(Long roomId, Long bookingId, String authorization) {
        try {
            hotelClient.release(roomId, bookingId, authorization);
            LOGGER.info("Released room hold for booking {}", bookingId);
            return true;
        } catch (RuntimeException compensationFailure) {
            LOGGER.error("Failed to release room hold for booking {}", bookingId, compensationFailure);
            return false;
        }
    }
}
