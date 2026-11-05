package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.dto.ConfirmRoomAvailabilityRequest;
import com.kseniia.hotelbooking.hotel.dto.CreateRoomRequest;
import com.kseniia.hotelbooking.hotel.dto.ReleaseRoomRequest;
import com.kseniia.hotelbooking.hotel.dto.RoomResponse;
import com.kseniia.hotelbooking.hotel.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<RoomResponse> getAvailableRooms() {
        return roomService.getAvailableRooms();
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<RoomResponse> getRecommendedRooms() {
        return roomService.getRecommendedRooms();
    }

    @PostMapping("/{id}/confirm-availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SERVICE')")
    public void confirmAvailability(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmRoomAvailabilityRequest request
    ) {
        roomService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SERVICE')")
    public void release(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseRoomRequest request
    ) {
        roomService.release(id, request);
    }
}
