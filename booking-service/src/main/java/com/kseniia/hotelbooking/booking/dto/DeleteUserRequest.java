package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteUserRequest(
        @NotBlank @Size(max = 255) @Utf8ByteLength(max = 255) String username
) {
}
