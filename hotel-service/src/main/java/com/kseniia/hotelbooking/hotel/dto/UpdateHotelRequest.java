package com.kseniia.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateHotelRequest(
        @Schema(description = "Новое название отеля")
        @NotBlank String name,
        @Schema(description = "Новый адрес отеля")
        @NotBlank String address
) {
}
