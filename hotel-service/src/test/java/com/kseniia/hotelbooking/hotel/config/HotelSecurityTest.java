package com.kseniia.hotelbooking.hotel.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=hotel-security-test-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class HotelSecurityTest {

    private static final String SECRET = "hotel-security-test-secret-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedApiRequest() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenFromWrongIssuer() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token("other-service", "USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + token("booking-service", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Aurora",
                                  "address": "1 Central Street"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotCreateRoom() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token("booking-service", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": 1,
                                  "number": "101"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanReadHotels() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token("booking-service", "USER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + token("booking-service", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Admin Hotel",
                                  "address": "2 Central Street"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidRole() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token("booking-service", "GUEST")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTwoHourToken() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token(
                                "booking-service", "alice", 1L, "USER", 7_200)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header("Authorization", "Bearer " + token(
                                "booking-service", "alice", null, "USER", 3_600)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String[] parts = token("booking-service", "USER").split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        mockMvc.perform(get("/api/hotels").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsOpenApiPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void directErrorRequestRemainsProtected() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized());
    }

    private String token(String issuer, String role) throws Exception {
        return token(issuer, "alice", 1L, role, 3_600);
    }

    private String token(String issuer, String subject, Long userId, String role, long lifetimeSeconds)
            throws Exception {
        Instant issuedAt = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("role", role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(lifetimeSeconds)));
        if (userId != null) {
            claims.claim("userId", userId);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
