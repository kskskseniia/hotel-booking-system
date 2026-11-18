package com.kseniia.hotelbooking.gateway;

import com.kseniia.hotelbooking.gateway.error.GlobalErrorWebExceptionHandler;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.PrematureCloseException;

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import(GatewayErrorResponseHttpTest.ConnectionRefusedRouteConfiguration.class)
class GatewayErrorResponseHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GlobalErrorWebExceptionHandler errorHandler;

    @Test
    void returnsUnifiedErrorForUnknownRoute() {
        client().get()
                .uri("/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isEqualTo("Resource not found")
                .jsonPath("$.path").isEqualTo("/unknown")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void returnsUnifiedErrorWhenDownstreamServiceIsUnavailable() {
        client().get()
                .uri("/api/hotels")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").isEqualTo("Service unavailable")
                .jsonPath("$.path").isEqualTo("/api/hotels")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void returnsServiceUnavailableWhenRegisteredDownstreamRefusesConnection() {
        client().get()
                .uri("/connection-refused")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").isEqualTo("Service unavailable")
                .jsonPath("$.path").isEqualTo("/connection-refused")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void returnsServiceUnavailableForDownstreamTransportFailures() {
        List<Throwable> failures = List.of(
                new UnknownHostException("unknown downstream host"),
                new SocketTimeoutException("downstream socket timeout"),
                new TimeoutException("downstream response timeout"),
                ReadTimeoutException.INSTANCE,
                new SSLHandshakeException("downstream TLS handshake failed"),
                new SocketException("connection reset"),
                new ConnectTimeoutException("downstream connect timeout"),
                mock(PrematureCloseException.class),
                new WebClientRequestException(
                        new UnknownHostException("wrapped downstream host"),
                        HttpMethod.GET,
                        URI.create("http://downstream.invalid"),
                        HttpHeaders.EMPTY
                ),
                new DecoderException(new SSLHandshakeException("wrapped downstream TLS failure"))
        );

        for (Throwable failure : failures) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/transport-failure").build()
            );

            errorHandler.handle(exchange, failure).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as("status for %s", failure.getClass().getSimpleName())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void returnsInternalServerErrorForUnrelatedWrappedTransportFailure() {
        List<Throwable> failures = List.of(
                new TimeoutException("application timeout"),
                new SocketException("application socket failure"),
                new SSLHandshakeException("application TLS failure")
        );

        for (Throwable failure : failures) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/application-failure").build()
            );

            errorHandler.handle(exchange, new RuntimeException("application failed", failure)).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as("status for wrapped %s", failure.getClass().getSimpleName())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    void preservesMethodNotAllowedHeader() {
        client().post()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().valueEquals(HttpHeaders.ALLOW, "GET")
                .expectBody()
                .jsonPath("$.status").isEqualTo(405)
                .jsonPath("$.error").isEqualTo("Method Not Allowed");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConnectionRefusedRouteConfiguration {

        @Bean
        RouteLocator connectionRefusedRoute(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("connection-refused", route -> route
                            .path("/connection-refused")
                            .uri("http://127.0.0.1:1"))
                    .build();
        }
    }
}