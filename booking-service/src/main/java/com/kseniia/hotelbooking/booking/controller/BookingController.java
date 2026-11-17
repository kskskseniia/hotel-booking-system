package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.dto.AdminRoomBookingResponse;
import com.kseniia.hotelbooking.booking.dto.BookingResponse;
import com.kseniia.hotelbooking.booking.dto.CreateBookingRequest;
import com.kseniia.hotelbooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/booking")
    @Operation(
            summary = "Создание бронирования",
            description = "Бронирует указанный номер или автоматически выбирает рекомендованный. requestId защищает повторный запрос от создания дубликата.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "Booking confirmed")
    )
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        Number userId = jwt.getClaim("userId");
        BookingResponse booking = bookingService.create(request, userId.longValue(), authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/bookings")
    @Operation(
            summary = "История бронирований пользователя",
            description = "Возвращает все бронирования текущего пользователя, включая отменённые.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Authenticated user's booking history")
    )
    public List<BookingResponse> getHistory(@AuthenticationPrincipal Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return bookingService.getHistory(userId.longValue());
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(
            summary = "Получение бронирования",
            description = "Возвращает бронирование по bookingId, если оно принадлежит текущему пользователю.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Authenticated user's booking")
    )
    public BookingResponse get(@PathVariable Long bookingId, @AuthenticationPrincipal Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return bookingService.get(bookingId, userId.longValue());
    }

    @DeleteMapping("/booking/{bookingId}")
    @Operation(
            summary = "Отмена бронирования",
            description = "Освобождает номер в Hotel Service и переводит бронирование текущего пользователя в статус CANCELLED.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "204", description = "Booking cancelled")
    )
    public ResponseEntity<Void> cancel(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        Number userId = jwt.getClaim("userId");
        bookingService.cancel(bookingId, userId.longValue(), authorization);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookings/rooms/{roomId}")
    @Operation(
            summary = "История бронирований номера",
            description = "Возвращает администратору все бронирования указанного номера.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Room booking history for administrator")
    )
    public List<AdminRoomBookingResponse> getRoomBookings(@PathVariable Long roomId) {
        return bookingService.getRoomBookings(roomId);
    }
}
