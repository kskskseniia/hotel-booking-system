package com.kseniia.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomRequest(
        @Schema(description = "Отель, к которому относится номер")
        @NotNull Long hotelId,
        @Schema(description = "Новое обозначение номера")
        @NotBlank String number,
        @Schema(description = "Можно ли предлагать номер для новых бронирований")
        @NotNull Boolean available
) {
}
