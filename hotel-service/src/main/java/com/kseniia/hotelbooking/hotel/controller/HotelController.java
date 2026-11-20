package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.dto.CreateHotelRequest;
import com.kseniia.hotelbooking.hotel.dto.HotelResponse;
import com.kseniia.hotelbooking.hotel.dto.UpdateHotelRequest;
import com.kseniia.hotelbooking.hotel.service.HotelService;
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
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Создание отеля",
            description = "Добавляет новый отель. Доступно только администратору."
    )
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody CreateHotelRequest request) {
        HotelResponse hotel = hotelService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(hotel);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Список отелей",
            description = "Возвращает все зарегистрированные отели пользователю или администратору."
    )
    public List<HotelResponse> getHotels() {
        return hotelService.getHotels();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Обновление отеля",
            description = "Полностью заменяет название и адрес отеля. Доступно только администратору."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отель обновлён"),
            @ApiResponse(responseCode = "404", description = "Отель не найден")
    })
    public HotelResponse updateHotel(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHotelRequest request
    ) {
        return hotelService.updateHotel(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Удаление отеля",
            description = "Удаляет отель без номеров. Доступно только администратору."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отель удалён"),
            @ApiResponse(responseCode = "404", description = "Отель не найден"),
            @ApiResponse(responseCode = "409", description = "В отеле ещё есть номера")
    })
    public void deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
    }
}
