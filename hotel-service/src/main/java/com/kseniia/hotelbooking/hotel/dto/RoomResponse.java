package com.kseniia.hotelbooking.hotel.dto;

public record RoomResponse(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {
}
