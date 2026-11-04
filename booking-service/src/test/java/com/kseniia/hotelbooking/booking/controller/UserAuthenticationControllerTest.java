package com.kseniia.hotelbooking.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
@AutoConfigureMockMvc
class UserAuthenticationControllerTest {

    private static final String JWT_SECRET = "test-only-jwt-secret-32-bytes-minimum!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM bookings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void registersUserAndReturnsSignedOneHourJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content(credentials("alice", "strong-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE username = ?",
                String.class,
                "alice"
        );
        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE username = ?",
                String.class,
                "alice"
        );

        assertThat(passwordHash).isNotEqualTo("strong-password");
        assertThat(new BCryptPasswordEncoder().matches("strong-password", passwordHash)).isTrue();
        assertThat(role).isEqualTo("USER");

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        SignedJWT jwt = SignedJWT.parse(response.get("token").asText());

        assertThat(jwt.verify(new MACVerifier(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("alice");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("USER");
        assertThat(jwt.getJWTClaimsSet().getLongClaim("userId")).isPositive();
        assertThat(response.get("expiresAt").asText())
                .isEqualTo(jwt.getJWTClaimsSet().getExpirationTime().toInstant().toString());
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().getTime()
                - jwt.getJWTClaimsSet().getIssueTime().getTime()).isEqualTo(3_600_000L);
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content(credentials("alice", "first-password")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content(credentials("alice", "second-password")))
                .andExpect(status().isConflict());

        Long users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Long.class,
                "alice"
        );
        assertThat(users).isEqualTo(1L);
    }

    @Test
    void rejectsBlankRegistrationData() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content(credentials(" ", "")))
                .andExpect(status().isBadRequest());

        Long users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(users).isZero();
    }

    @Test
    void authenticatesExistingUser() throws Exception {
        String passwordHash = new BCryptPasswordEncoder().encode("strong-password");
        jdbcTemplate.update(
                "INSERT INTO users (username, password, role) VALUES (?, ?, ?)",
                "alice",
                passwordHash,
                "USER"
        );

        mockMvc.perform(post("/user/auth")
                        .contentType("application/json")
                        .content(credentials("alice", "strong-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        String passwordHash = new BCryptPasswordEncoder().encode("strong-password");
        jdbcTemplate.update(
                "INSERT INTO users (username, password, role) VALUES (?, ?, ?)",
                "alice",
                passwordHash,
                "USER"
        );

        mockMvc.perform(post("/user/auth")
                        .contentType("application/json")
                        .content(credentials("alice", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownUsername() throws Exception {
        mockMvc.perform(post("/user/auth")
                        .contentType("application/json")
                        .content(credentials("unknown", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    private String credentials(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
