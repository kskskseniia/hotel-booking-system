package com.kseniia.hotelbooking.hotel.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.util.Set;

public class HotelJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Set<String> USER_ROLES = Set.of("USER", "ADMIN");
    private static final String SERVICE_ROLE = "SERVICE";
    private static final String BOOKING_SERVICE_SUBJECT = "booking-service";
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Required booking JWT claims are invalid",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return failure();
        }
        if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
            return failure();
        }
        if (!Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).equals(TOKEN_LIFETIME)) {
            return failure();
        }

        String role = jwt.getClaimAsString("role");
        if (SERVICE_ROLE.equals(role)) {
            return BOOKING_SERVICE_SUBJECT.equals(jwt.getSubject())
                    ? OAuth2TokenValidatorResult.success()
                    : failure();
        }
        if (!USER_ROLES.contains(role) || !hasValidUserId(jwt)) {
            return failure();
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasValidUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        return userIdClaim instanceof Number userId
                && userId.longValue() > 0
                && userId.doubleValue() == Math.rint(userId.doubleValue());
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}