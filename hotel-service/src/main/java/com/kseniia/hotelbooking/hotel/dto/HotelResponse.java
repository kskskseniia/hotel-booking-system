package com.kseniia.hotelbooking.hotel.dto;

public record HotelResponse(
        Long hotelId,
        String name,
        String address
) {
}
