package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Schema(description = "Имя пользователя, роль которого нужно изменить")
        @NotBlank @Size(max = 255) @Utf8ByteLength(max = 255) String username,
        @Schema(description = "Новая роль пользователя")
        @NotNull UserRole role
) {
}
