package com.kseniia.hotelbooking.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ApiGatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void definesRoutesForDomainServices() {
        Map<String, RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectMap(RouteDefinition::getId)
                .block();

        assertThat(routes).isNotNull();
        assertThat(routes).containsKeys("hotel-service", "booking-service");
        assertThat(routes.get("hotel-service").getUri().toString()).isEqualTo("lb://hotel-service");
        assertThat(routes.get("booking-service").getUri().toString()).isEqualTo("lb://booking-service");

        assertThat(pathPatterns(routes.get("hotel-service")))
                .containsExactlyInAnyOrder("/api/hotels/**", "/api/rooms/**");
        assertThat(pathPatterns(routes.get("booking-service")))
                .containsExactlyInAnyOrder("/api/bookings/**", "/api/users/**", "/api/auth/**");
    }

    private List<String> pathPatterns(RouteDefinition routeDefinition) {
        return routeDefinition.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .flatMap(value -> Arrays.stream(value.split(",")))
                .toList();
    }

    @Test
    void usesExpectedLocalEndpoint() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
    }
}
