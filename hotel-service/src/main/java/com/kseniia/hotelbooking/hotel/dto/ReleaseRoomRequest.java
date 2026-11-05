package com.kseniia.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReleaseRoomRequest(
        @NotNull @Positive Long bookingId
) {
}
