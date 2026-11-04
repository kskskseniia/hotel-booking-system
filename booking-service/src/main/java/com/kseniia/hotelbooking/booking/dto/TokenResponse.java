package com.kseniia.hotelbooking.booking.dto;

import java.time.Instant;

public record TokenResponse(String token, Instant expiresAt) {
}
