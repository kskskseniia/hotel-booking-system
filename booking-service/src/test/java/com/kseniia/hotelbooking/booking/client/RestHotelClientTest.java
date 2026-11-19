package com.kseniia.hotelbooking.booking.client;

import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import com.kseniia.hotelbooking.booking.service.JwtTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestHotelClientTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<Long> requestTimes = new CopyOnWriteArrayList<>();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile Duration responseDelay;
    private volatile String requestPath;
    private volatile String authorization;
    private volatile String requestBody;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 204;
        responseBody = "";
        responseDelay = Duration.ZERO;
        requestTimes.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void readsRoomIdsFromRecommendations() {
        responseStatus = 200;
        responseBody = """
                [{"roomId":42,"hotelId":7,"number":"101","available":true,"timesBooked":0}]
                """;

        assertThat(client().getRecommendedRooms("Bearer booking-token"))
                .extracting(RoomCandidate::roomId)
                .containsExactly(42L);
    }

    @Test
    void confirmsAvailabilityWithBookingCorrelationDataAndServiceJwt() {
        client().confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        );

        assertThat(requestCount).hasValue(1);
        assertThat(requestPath).isEqualTo("/api/rooms/42/confirm-availability");
        assertThat(authorization).isEqualTo("Bearer service-token");
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

    @Test
    void calculatesIncreasingBackoffForEachRetry() {
        Duration baseDelay = Duration.ofMillis(50);

        assertThat(RestHotelClient.retryDelay(baseDelay, 1)).isEqualTo(Duration.ofMillis(50));
        assertThat(RestHotelClient.retryDelay(baseDelay, 2)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void appliesCalculatedBackoffBetweenRetries() {
        responseStatus = 503;

        assertThatThrownBy(() -> client(
                RestClient.builder(),
                Duration.ofSeconds(5),
                3,
                Duration.ofMillis(50)
        ).confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isInstanceOf(HotelServiceUnavailableException.class);

        assertThat(requestTimes).hasSize(3);
        long firstBackoffMillis = Duration.ofNanos(requestTimes.get(1) - requestTimes.get(0)).toMillis();
        long secondBackoffMillis = Duration.ofNanos(requestTimes.get(2) - requestTimes.get(1)).toMillis();
        assertThat(firstBackoffMillis).isGreaterThanOrEqualTo(40L);
        assertThat(secondBackoffMillis).isGreaterThanOrEqualTo(90L);
    }

    @Test
    void retriesDelayedResponsesOnlyUpToConfiguredLimit() {
        responseDelay = Duration.ofMillis(250);
        AtomicInteger attempts = new AtomicInteger();
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    attempts.incrementAndGet();
                    return execution.execute(request, body);
                });

        assertThatThrownBy(() -> client(
                builder,
                Duration.ofMillis(50),
                3,
                Duration.ofMillis(10)
        ).confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isInstanceOf(HotelServiceUnavailableException.class)
                .hasRootCauseInstanceOf(HttpTimeoutException.class);

        assertThat(attempts).hasValue(3);
        assertThat(requestCount).hasPositiveValue();
    }

    @Test
    void mapsMissingLoadBalancerInstanceToHotelServiceUnavailable() {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveLoadBalancer.Factory<ServiceInstance> factory = emptyLoadBalancerFactory(attempts);
        BlockingLoadBalancerClient loadBalancerClient = new BlockingLoadBalancerClient(factory);
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient));

        assertThatThrownBy(() -> client(builder).confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isInstanceOf(HotelServiceUnavailableException.class);

        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotMapUnrelatedIllegalStateException() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("unrelated failure");
        RestClient.Builder builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            attempts.incrementAndGet();
            throw failure;
        });

        assertThatThrownBy(() -> client(builder).confirmAvailability(
                42L,
                101L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                "Bearer booking-token"
        )).isSameAs(failure);

        assertThat(attempts).hasValue(1);
    }

    private ReactiveLoadBalancer.Factory<ServiceInstance> emptyLoadBalancerFactory(AtomicInteger attempts) {
        return new ReactiveLoadBalancer.Factory<>() {
            @Override
            public LoadBalancerProperties getProperties(String serviceId) {
                return new LoadBalancerProperties();
            }

            @Override
            public ReactiveLoadBalancer<ServiceInstance> getInstance(String serviceId) {
                return request -> {
                    attempts.incrementAndGet();
                    return Mono.just(new EmptyResponse());
                };
            }

            @Override
            public <X> Map<String, X> getInstances(String serviceId, Class<X> type) {
                return Map.of();
            }

            @Override
            public <X> X getInstance(String serviceId, Class<?> type, Class<?>... generics) {
                return null;
            }
        };
    }

    private RestHotelClient client() {
        return client(RestClient.builder(), Duration.ofSeconds(5), 3, Duration.ZERO);
    }

    private RestHotelClient client(RestClient.Builder builder) {
        return client(builder, Duration.ofSeconds(5), 3, Duration.ZERO);
    }

    private RestHotelClient client(
            RestClient.Builder builder,
            Duration readTimeout,
            int maxAttempts,
            Duration retryBackoff
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newHttpClient()
        );
        requestFactory.setReadTimeout(readTimeout);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.issueServiceToken()).thenReturn("service-token");
        return new RestHotelClient(
                builder.requestFactory(requestFactory),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                maxAttempts,
                retryBackoff,
                jwtTokenService
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestTimes.add(System.nanoTime());
        requestPath = exchange.getRequestURI().getPath();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            Thread.sleep(responseDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            exchange.close();
            return;
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        if (body.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
