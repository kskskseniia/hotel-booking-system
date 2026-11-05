package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.entity.BookingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminRoomBookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        LocalDateTime createdAt
) {
}
