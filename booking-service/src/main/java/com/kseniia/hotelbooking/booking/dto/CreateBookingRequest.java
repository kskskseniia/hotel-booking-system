package com.kseniia.hotelbooking.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull Boolean autoSelect,
        Long roomId,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull @Future LocalDate endDate
) {
}
