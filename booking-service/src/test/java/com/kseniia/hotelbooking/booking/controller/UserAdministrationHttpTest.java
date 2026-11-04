package com.kseniia.hotelbooking.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kseniia.hotelbooking.booking.dto.TokenResponse;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import com.kseniia.hotelbooking.booking.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
        }
)
class UserAdministrationHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void adminCreatesUserOverRealHttp() throws Exception {
        String adminToken = issueToken("admin", UserRole.ADMIN);
        HttpHeaders headers = bearerHeaders(adminToken);
        Map<String, String> request = Map.of(
                "username", "manager",
                "password", "strong-password",
                "role", "USER"
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/user",
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("id").asLong()).isPositive();
        assertThat(responseBody.get("username").asText()).isEqualTo("manager");
        assertThat(responseBody.get("role").asText()).isEqualTo("USER");
        assertThat(responseBody.has("password")).isFalse();
        assertThat(responseBody.has("passwordHash")).isFalse();

        User createdUser = userRepository.findByUsername("manager").orElseThrow();
        assertThat(createdUser.getPasswordHash()).isNotEqualTo("strong-password");
        assertThat(passwordEncoder.matches("strong-password", createdUser.getPasswordHash())).isTrue();
    }

    @Test
    void regularUserCannotCreateUserOverRealHttp() {
        String userToken = issueToken("regular-user", UserRole.USER);
        HttpHeaders headers = bearerHeaders(userToken);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/user",
                new HttpEntity<>(Map.of(
                        "username", "forbidden-user",
                        "password", "strong-password",
                        "role", "USER"
                ), headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findByUsername("forbidden-user")).isEmpty();
    }

    @Test
    void duplicateUsernameReturnsConflictOverRealHttp() {
        HttpHeaders headers = bearerHeaders(issueToken("admin", UserRole.ADMIN));
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of(
                "username", "duplicate-user",
                "password", "strong-password",
                "role", "USER"
        ), headers);

        ResponseEntity<String> firstResponse = restTemplate.postForEntity("/user", request, String.class);
        ResponseEntity<String> duplicateResponse = restTemplate.postForEntity("/user", request, String.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Long.class,
                "duplicate-user"
        )).isEqualTo(1L);
    }

    @Test
    void missingTokenReturnsUnauthorizedOverRealHttp() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/user",
                Map.of(
                        "username", "unauthorized-user",
                        "password", "strong-password",
                        "role", "USER"
                ),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findByUsername("unauthorized-user")).isEmpty();
    }

    @Test
    void invalidRequestReturnsBadRequestOverRealHttp() {
        HttpHeaders headers = bearerHeaders(issueToken("admin", UserRole.ADMIN));

        ResponseEntity<String> blankUsernameResponse = restTemplate.postForEntity(
                "/user",
                new HttpEntity<>(Map.of(
                        "username", " ",
                        "password", "strong-password",
                        "role", "USER"
                ), headers),
                String.class
        );
        ResponseEntity<String> missingRoleResponse = restTemplate.postForEntity(
                "/user",
                new HttpEntity<>(Map.of(
                        "username", "missing-role",
                        "password", "strong-password"
                ), headers),
                String.class
        );
        ResponseEntity<String> unknownRoleResponse = restTemplate.postForEntity(
                "/user",
                new HttpEntity<>(Map.of(
                        "username", "unknown-role",
                        "password", "strong-password",
                        "role", "OWNER"
                ), headers),
                String.class
        );

        assertThat(blankUsernameResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingRoleResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownRoleResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);
    }

    private String issueToken(String username, UserRole role) {
        User user = userRepository.saveAndFlush(new User(
                username,
                passwordEncoder.encode("token-owner-password"),
                role
        ));
        TokenResponse token = jwtTokenService.issueToken(user);
        return token.token();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
