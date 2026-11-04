package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.service.UserAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
        }
)
class UserAuthenticationUnexpectedFailureHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private UserAuthenticationService authenticationService;

    @Test
    void unexpectedPublicAuthenticationFailureReturnsInternalServerError() {
        when(authenticationService.register(any())).thenThrow(new IllegalStateException("test failure"));

        HttpStatus status = HttpStatus.valueOf(restTemplate.postForEntity(
                "/user/register",
                Map.of("username", "alice", "password", "strong-password"),
                String.class
        ).getStatusCode().value());

        assertThat(status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
