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
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");
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

        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)
                || userId.longValue() <= 0
                || userId.doubleValue() != Math.rint(userId.doubleValue())) {
            return failure();
        }

        if (!ALLOWED_ROLES.contains(jwt.getClaimAsString("role"))) {
            return failure();
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}