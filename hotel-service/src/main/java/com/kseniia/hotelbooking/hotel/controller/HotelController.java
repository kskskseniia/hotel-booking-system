package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.dto.CreateHotelRequest;
import com.kseniia.hotelbooking.hotel.dto.HotelResponse;
import com.kseniia.hotelbooking.hotel.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody CreateHotelRequest request) {
        HotelResponse hotel = hotelService.createHotel(request);
        return ResponseEntity.created(URI.create("/api/hotels/" + hotel.id())).body(hotel);
    }
}
