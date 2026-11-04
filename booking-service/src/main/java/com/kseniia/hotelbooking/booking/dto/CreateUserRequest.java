package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Utf8ByteLength(max = 72) String password,
        @NotNull UserRole role
) {
}
