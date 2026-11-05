package com.kseniia.hotelbooking.hotel.service;

import com.kseniia.hotelbooking.hotel.dto.CreateRoomRequest;
import com.kseniia.hotelbooking.hotel.dto.ConfirmRoomAvailabilityRequest;
import com.kseniia.hotelbooking.hotel.dto.RoomResponse;
import com.kseniia.hotelbooking.hotel.dto.ReleaseRoomRequest;
import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.entity.Room;
import com.kseniia.hotelbooking.hotel.entity.RoomHold;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomHoldRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomHoldRepository roomHoldRepository;

    public RoomService(
            RoomRepository roomRepository,
            HotelRepository hotelRepository,
            RoomHoldRepository roomHoldRepository
    ) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomHoldRepository = roomHoldRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hotel not found"));
        Room room = roomRepository.save(new Room(hotel, request.number()));
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAvailableRooms() {
        return roomRepository.findAllByAvailableTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRecommendedRooms() {
        return roomRepository.findAllByAvailableTrueOrderByTimesBookedAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void confirmAvailability(Long roomId, ConfirmRoomAvailabilityRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new ResponseStatusException(BAD_REQUEST, "End date must be after start date");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Room not found"));

        RoomHold existingHold = roomHoldRepository.findByBookingId(request.bookingId()).orElse(null);
        if (existingHold != null) {
            boolean sameRequest = existingHold.getRoom().getId().equals(roomId)
                    && existingHold.getStartDate().equals(request.startDate())
                    && existingHold.getEndDate().equals(request.endDate());
            if (sameRequest) {
                return;
            }
            throw new ResponseStatusException(CONFLICT, "Booking already holds a different room or date range");
        }

        if (!room.isAvailable()) {
            throw new ResponseStatusException(CONFLICT, "Room is not operationally available");
        }
        boolean datesOverlap = roomHoldRepository.existsByRoomIdAndStartDateLessThanAndEndDateGreaterThan(
                roomId,
                request.endDate(),
                request.startDate()
        );
        if (datesOverlap) {
            throw new ResponseStatusException(CONFLICT, "Room is already held for the requested dates");
        }

        roomHoldRepository.save(new RoomHold(
                room,
                request.bookingId(),
                request.startDate(),
                request.endDate()
        ));
        room.incrementTimesBooked();
    }

    @Transactional
    public void release(Long roomId, ReleaseRoomRequest request) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Room not found"));
        RoomHold hold = roomHoldRepository.findByBookingId(request.bookingId()).orElse(null);
        if (hold == null) {
            return;
        }
        if (!hold.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(CONFLICT, "Booking holds a different room");
        }

        roomHoldRepository.delete(hold);
        room.decrementTimesBooked();
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked()
        );
    }
}
