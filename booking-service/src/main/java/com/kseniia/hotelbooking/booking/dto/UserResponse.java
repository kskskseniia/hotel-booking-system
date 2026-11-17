package com.kseniia.hotelbooking.booking.dto;

import com.kseniia.hotelbooking.booking.entity.UserRole;

public record UserResponse(
        Long userId,
        String username,
        UserRole role
) {
}
