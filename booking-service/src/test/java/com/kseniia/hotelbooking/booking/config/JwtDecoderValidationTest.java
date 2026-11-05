package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.service.JwtTokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
class JwtDecoderValidationTest {

    private static final String JWT_SECRET = "test-only-jwt-secret-32-bytes-minimum!";

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void acceptsCompleteBookingServiceToken() throws Exception {
        String token = token("booking-service", "alice", 1L, "USER", Instant.now(), 3_600);

        assertThatCode(() -> jwtDecoder.decode(token)).doesNotThrowAnyException();
        assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo("alice");
    }

    @Test
    void issuesServiceTokenForHotelOperations() throws Exception {
        String token = jwtTokenService.issueServiceToken();

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("booking-service");
        assertThat(claims.getStringClaim("role")).isEqualTo("SERVICE");
        assertThat(claims.getClaim("userId")).isNull();
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plusSeconds(3_600));
    }

    @Test
    void rejectsMissingRequiredClaims() throws Exception {
        Instant issuedAt = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("booking-service")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(3_600)))
                .subject("alice")
                .build();

        assertRejected(sign(claims));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        assertRejected(token("attacker", "alice", 1L, "USER", Instant.now(), 3_600));
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        assertRejected(token("booking-service", "alice", 1L, "SUPER_ADMIN", Instant.now(), 3_600));
    }

    @Test
    void rejectsLifetimeOtherThanOneHour() throws Exception {
        assertRejected(token("booking-service", "alice", 1L, "ADMIN", Instant.now(), 7_200));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        assertRejected(token("booking-service", "alice", 1L, "USER", Instant.now().minusSeconds(7_200), 3_600));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String token = token("booking-service", "alice", 1L, "USER", Instant.now(), 3_600);
        String[] parts = token.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        assertRejected(tampered);
    }

    private String token(
            String issuer,
            String subject,
            Long userId,
            String role,
            Instant issuedAt,
            long lifetimeSeconds
    ) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(lifetimeSeconds)))
                .subject(subject)
                .claim("userId", userId)
                .claim("role", role)
                .build();
        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
