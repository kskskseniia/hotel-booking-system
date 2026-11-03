package com.kseniia.hotelbooking.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import(CorrelationIdFilterTest.TestEndpointConfiguration.class)
class CorrelationIdFilterTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void generatesAndPropagatesCorrelationIdWhenHeaderIsMissing() {
        EntityExchangeResult<String> result = webTestClient.get()
                .uri("/test/correlation")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(CORRELATION_ID_HEADER)
                .expectBody(String.class)
                .returnResult();

        String correlationId = result.getResponseHeaders().getFirst(CORRELATION_ID_HEADER);
        assertThat(result.getResponseBody()).isEqualTo(correlationId);
        assertThatCode(() -> UUID.fromString(correlationId)).doesNotThrowAnyException();
    }

    @Test
    void propagatesExistingCorrelationId() {
        String correlationId = "client-correlation-id";

        webTestClient.get()
                .uri("/test/correlation")
                .header(CORRELATION_ID_HEADER, correlationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CORRELATION_ID_HEADER, correlationId)
                .expectBody(String.class).isEqualTo(correlationId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointConfiguration {

        @Bean
        RouterFunction<ServerResponse> correlationEndpoint() {
            return route(GET("/test/correlation"), request -> ServerResponse.ok()
                    .bodyValue(request.headers().firstHeader(CORRELATION_ID_HEADER) == null
                            ? "missing"
                            : request.headers().firstHeader(CORRELATION_ID_HEADER)));
        }
    }
}
