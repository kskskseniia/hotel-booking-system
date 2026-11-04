package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.dto.TokenResponse;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
        }
)
class UserAuthenticationHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM bookings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void duplicateRegistrationReturnsConflictOverRealHttp() {
        Map<String, String> request = Map.of(
                "username", "alice",
                "password", "strong-password"
        );

        ResponseEntity<TokenResponse> firstResponse = restTemplate.postForEntity(
                "/user/register",
                request,
                TokenResponse.class
        );
        ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(
                "/user/register",
                request,
                String.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidCredentialsReturnBadRequestOverRealHttp() {
        assertThat(register(" ", "").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("a".repeat(256), "strong-password").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("alice", "a".repeat(73)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("alice", "я".repeat(37)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void malformedJsonReturnsBadRequestOverRealHttp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/user/register",
                new HttpEntity<>("{not-json", headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void directErrorEndpointRemainsProtected() {
        assertThat(restTemplate.getForEntity("/error", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void concurrentDuplicateRegistrationCreatesOneUser() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpStatus> first = executor.submit(() -> {
                start.await();
                return HttpStatus.valueOf(register("concurrent-user", "strong-password").getStatusCode().value());
            });
            Future<HttpStatus> second = executor.submit(() -> {
                start.await();
                return HttpStatus.valueOf(register("concurrent-user", "strong-password").getStatusCode().value());
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?",
                    Long.class,
                    "concurrent-user"
            )).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private ResponseEntity<String> register(String username, String password) {
        return restTemplate.postForEntity(
                "/user/register",
                Map.of("username", username, "password", password),
                String.class
        );
    }
}
