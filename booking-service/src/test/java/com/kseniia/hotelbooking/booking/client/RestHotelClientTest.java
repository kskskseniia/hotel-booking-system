package com.kseniia.hotelbooking.booking.client;

import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestHotelClientTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile String requestPath;
    private volatile String authorization;
    private volatile String requestBody;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 204;
        responseBody = "";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void confirmsAvailabilityWithBookingCorrelationDataAndJwt() {
        client().confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        );

        assertThat(requestCount).hasValue(1);
        assertThat(requestPath).isEqualTo("/api/rooms/42/confirm-availability");
        assertThat(authorization).isEqualTo("Bearer booking-token");
        assertThat(requestBody)
                .contains("\"bookingId\":101")
                .contains("\"startDate\":\"2099-07-10\"")
                .contains("\"endDate\":\"2099-07-12\"");
    }

    @Test
    void doesNotRetryRoomConflict() {
        responseStatus = 409;

        assertThatThrownBy(() -> client().confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isInstanceOf(RoomUnavailableException.class);

        assertThat(requestCount).hasValue(1);
    }

    @Test
    void retriesServerFailureOnlyUpToConfiguredLimit() {
        responseStatus = 503;

        assertThatThrownBy(() -> client().confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isInstanceOf(HotelServiceUnavailableException.class);

        assertThat(requestCount).hasValue(3);
    }

    private RestHotelClient client() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newHttpClient()
        );
        return new RestHotelClient(
                RestClient.builder().requestFactory(requestFactory),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                3,
                Duration.ZERO
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestPath = exchange.getRequestURI().getPath();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        if (body.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
