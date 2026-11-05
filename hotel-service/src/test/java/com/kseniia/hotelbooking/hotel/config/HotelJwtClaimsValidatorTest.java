package com.kseniia.hotelbooking.hotel.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HotelJwtClaimsValidatorTest {

    private final HotelJwtClaimsValidator validator = new HotelJwtClaimsValidator();

    @Test
    void acceptsBookingServiceToken() {
        OAuth2TokenValidatorResult result = validator.validate(serviceJwt("booking-service"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsServiceTokenWithUnexpectedSubject() {
        OAuth2TokenValidatorResult result = validator.validate(serviceJwt("ordinary-user"));

        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt serviceJwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuer("booking-service")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "SERVICE")
                .build();
    }
}
