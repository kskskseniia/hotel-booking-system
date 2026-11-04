package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.dto.CreateHotelRequest;
import com.kseniia.hotelbooking.hotel.dto.HotelResponse;
import com.kseniia.hotelbooking.hotel.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody CreateHotelRequest request) {
        HotelResponse hotel = hotelService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(hotel);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<HotelResponse> getHotels() {
        return hotelService.getHotels();
    }
}
