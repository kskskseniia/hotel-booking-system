package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.dto.ConfirmRoomAvailabilityRequest;
import com.kseniia.hotelbooking.hotel.dto.CreateRoomRequest;
import com.kseniia.hotelbooking.hotel.dto.ReleaseRoomRequest;
import com.kseniia.hotelbooking.hotel.dto.RoomResponse;
import com.kseniia.hotelbooking.hotel.dto.UpdateRoomRequest;
import com.kseniia.hotelbooking.hotel.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    @Operation(
            summary = "Создание номера",
            description = "Добавляет номер в существующий отель. Доступно только администратору."
    )
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Список доступных номеров",
            description = "Возвращает номера, которые разрешено предлагать для нового бронирования."
    )
    public List<RoomResponse> getAvailableRooms() {
        return roomService.getAvailableRooms();
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Рекомендованные номера",
            description = "Возвращает доступные номера, начиная с тех, которые бронировали реже всего."
    )
    public List<RoomResponse> getRecommendedRooms() {
        return roomService.getRecommendedRooms();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Обновление номера",
            description = "Полностью обновляет отель, обозначение и доступность номера. Счётчик бронирований не изменяется."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Номер обновлён"),
            @ApiResponse(responseCode = "404", description = "Номер или отель не найден")
    })
    public RoomResponse updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return roomService.updateRoom(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Удаление номера",
            description = "Удаляет номер, если для него нет активного удержания."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Номер удалён"),
            @ApiResponse(responseCode = "404", description = "Номер не найден"),
            @ApiResponse(responseCode = "409", description = "Для номера существует активное удержание")
    })
    public void deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
    }

    @PostMapping("/{id}/confirm-availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
            summary = "Подтверждение доступности номера",
            description = "Внутренний вызов Booking Service: проверяет даты и закрепляет номер за bookingId."
    )
    public void confirmAvailability(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmRoomAvailabilityRequest request
    ) {
        roomService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
            summary = "Освобождение номера",
            description = "Внутренний вызов Booking Service: снимает закрепление номера для указанного bookingId."
    )
    public void release(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseRoomRequest request
    ) {
        roomService.release(id, request);
    }
}
