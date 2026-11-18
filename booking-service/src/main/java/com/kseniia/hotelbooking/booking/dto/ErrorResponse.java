package com.kseniia.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ErrorResponse(
        @Schema(description = "UTC time when the error occurred", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,
        @Schema(description = "HTTP status code", example = "409", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Standard HTTP reason phrase", example = "Conflict", requiredMode = Schema.RequiredMode.REQUIRED)
        String error,
        @Schema(description = "Safe error details", example = "Username already exists", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Request path", example = "/user/register", requiredMode = Schema.RequiredMode.REQUIRED)
        String path
) {
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
    }
}