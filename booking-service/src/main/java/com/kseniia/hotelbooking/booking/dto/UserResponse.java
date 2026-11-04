package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.entity.UserRole;

public record UserResponse(
        Long id,
        String username,
        UserRole role
) {
}
