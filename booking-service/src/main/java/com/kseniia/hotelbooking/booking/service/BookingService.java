package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.client.RoomCandidate;
import com.kseniia.hotelbooking.booking.dto.AdminRoomBookingResponse;
import com.kseniia.hotelbooking.booking.dto.BookingResponse;
import com.kseniia.hotelbooking.booking.dto.CreateBookingRequest;
import com.kseniia.hotelbooking.booking.entity.BookingRequestStatus;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Service
public class BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingService.class);
    private static final long IDEMPOTENCY_POLL_MILLIS = 25;

    private final BookingStateService bookingStateService;
    private final HotelClient hotelClient;
    private final Duration idempotencyWaitTimeout;

    public BookingService(
            BookingStateService bookingStateService,
            HotelClient hotelClient,
            @Value("${booking.idempotency-wait-timeout}") Duration idempotencyWaitTimeout
    ) {
        this.bookingStateService = bookingStateService;
        this.hotelClient = hotelClient;
        this.idempotencyWaitTimeout = idempotencyWaitTimeout;
    }

    public BookingResponse create(
            CreateBookingRequest request,
            Long userId,
            String authorization
    ) {
        validate(request);
        Long requestedRoomId = request.autoSelect() ? null : request.roomId();
        BookingStateService.PendingBookingResult existing = bookingStateService.findByRequest(
                userId,
                request.requestId(),
                request.autoSelect(),
                requestedRoomId,
                request.startDate(),
                request.endDate()
        );
        if (existing != null) {
            return replay(existing, userId, request.requestId());
        }
        Long initialRoomId = request.autoSelect() ? null : request.roomId();
        BookingStateService.PendingBookingResult pendingResult = bookingStateService.createPending(
                userId,
                request.requestId(),
                request.autoSelect(),
                requestedRoomId,
                initialRoomId,
                request.startDate(),
                request.endDate()
        );
        BookingResponse pending = pendingResult.booking();
        if (!pendingResult.created()) {
            return replay(pendingResult, userId, request.requestId());
        }

        if (request.autoSelect()) {
            List<Long> roomIds;
            try {
                roomIds = recommendedRoomIds(authorization);
            } catch (RoomUnavailableException exception) {
                bookingStateService.failRequest(pending.id(), BookingRequestStatus.ROOM_UNAVAILABLE, true);
                throw exception;
            } catch (RuntimeException exception) {
                bookingStateService.failRequest(pending.id(), BookingRequestStatus.HOTEL_UNAVAILABLE, true);
                throw asHotelUnavailable(exception);
            }
            return confirmFirstAvailable(pending.id(), roomIds, request, authorization);
        }
        return confirmSelectedRoom(pending.id(), initialRoomId, request, authorization);
    }

    private BookingResponse confirmFirstAvailable(
            Long bookingId,
            List<Long> roomIds,
            CreateBookingRequest request,
            String authorization
    ) {
        for (Long roomId : roomIds) {
            try {
                bookingStateService.assignRoom(bookingId, roomId);
            } catch (RuntimeException exception) {
                bookingStateService.failRequest(bookingId, BookingRequestStatus.HOTEL_UNAVAILABLE, true);
                throw asHotelUnavailable(exception);
            }
            try {
                return confirm(bookingId, roomId, request, authorization);
            } catch (RoomUnavailableException exception) {
                LOGGER.info("Room {} is unavailable for booking {}, trying next candidate", roomId, bookingId);
            } catch (RuntimeException exception) {
                handleFailure(roomId, bookingId, authorization, exception);
                throw asHotelUnavailable(exception);
            }
        }
        bookingStateService.failRequest(bookingId, BookingRequestStatus.ROOM_UNAVAILABLE, true);
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
            handleFailure(roomId, bookingId, authorization, exception);
            if (exception instanceof RoomUnavailableException roomUnavailableException) {
                throw roomUnavailableException;
            }
            throw asHotelUnavailable(exception);
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

    private void handleFailure(
            Long roomId,
            Long bookingId,
            String authorization,
            RuntimeException exception
    ) {
        BookingRequestStatus requestStatus = exception instanceof RoomUnavailableException
                ? BookingRequestStatus.ROOM_UNAVAILABLE
                : BookingRequestStatus.HOTEL_UNAVAILABLE;
        boolean compensated = compensate(roomId, bookingId, authorization);
        bookingStateService.failRequest(bookingId, requestStatus, compensated);
        if (compensated) {
            LOGGER.warn("Booking {} transitioned PENDING -> CANCELLED", bookingId);
        } else {
            LOGGER.error("Booking {} remains PENDING for compensation retry", bookingId);
        }
    }

    private BookingResponse replay(
            BookingStateService.PendingBookingResult result,
            Long userId,
            String requestId
    ) {
        if (result.requestStatus() == BookingRequestStatus.PROCESSING) {
            result = awaitTerminalResult(userId, requestId);
        }
        return switch (result.requestStatus()) {
            case SUCCEEDED -> result.booking();
            case ROOM_UNAVAILABLE -> throw new RoomUnavailableException();
            case HOTEL_UNAVAILABLE -> throw new HotelServiceUnavailableException();
            case PROCESSING -> throw new HotelServiceUnavailableException();
        };
    }

    private BookingStateService.PendingBookingResult awaitTerminalResult(Long userId, String requestId) {
        long deadline = System.nanoTime() + idempotencyWaitTimeout.toNanos();
        BookingStateService.PendingBookingResult result;
        do {
            try {
                Thread.sleep(IDEMPOTENCY_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new HotelServiceUnavailableException(exception);
            }
            result = bookingStateService.findByRequestId(userId, requestId);
            if (result == null) {
                throw new HotelServiceUnavailableException();
            }
        } while (result.requestStatus() == BookingRequestStatus.PROCESSING
                && System.nanoTime() < deadline);
        return result;
    }

    public List<BookingResponse> getHistory(Long userId) {
        return bookingStateService.getHistory(userId);
    }

    public BookingResponse get(Long bookingId, Long userId) {
        return bookingStateService.get(bookingId, userId);
    }

    public void cancel(Long bookingId, Long userId, String authorization) {
        BookingResponse booking = bookingStateService.get(bookingId, userId);
        if (booking.status() == BookingStatus.CANCELLED) {
            return;
        }
        if (booking.roomId() != null) {
            hotelClient.release(booking.roomId(), bookingId, authorization);
        }
        bookingStateService.cancel(bookingId, userId);
        LOGGER.info("Booking {} transitioned {} -> CANCELLED", bookingId, booking.status());
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

    private HotelServiceUnavailableException asHotelUnavailable(RuntimeException exception) {
        if (exception instanceof HotelServiceUnavailableException hotelServiceUnavailableException) {
            return hotelServiceUnavailableException;
        }
        return new HotelServiceUnavailableException(exception);
    }
}
