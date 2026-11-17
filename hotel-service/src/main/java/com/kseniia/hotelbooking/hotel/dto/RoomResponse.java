package com.kseniia.hotelbooking.hotel.dto;

public record RoomResponse(
        Long roomId,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {
}
