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
        assertThat(routes).containsOnlyKeys(
                "hotel-service",
                "hotel-service-create-room",
                "hotel-service-list-rooms",
                "hotel-service-recommend-rooms",
                "booking-service-create",
                "booking-service-history",
                "booking-service-room-history",
                "booking-service-item"
        );
        assertThat(routes.get("hotel-service").getUri().toString()).isEqualTo("lb://hotel-service");
        assertThat(routes.get("hotel-service-create-room").getUri().toString()).isEqualTo("lb://hotel-service");
        assertThat(routes.get("hotel-service-list-rooms").getUri().toString()).isEqualTo("lb://hotel-service");
        assertThat(routes.get("hotel-service-recommend-rooms").getUri().toString()).isEqualTo("lb://hotel-service");
        assertThat(routes.get("booking-service-create").getUri().toString()).isEqualTo("lb://booking-service");
        assertThat(routes.get("booking-service-history").getUri().toString()).isEqualTo("lb://booking-service");
        assertThat(routes.get("booking-service-room-history").getUri().toString()).isEqualTo("lb://booking-service");
        assertThat(routes.get("booking-service-item").getUri().toString()).isEqualTo("lb://booking-service");

        assertThat(pathPatterns(routes.get("hotel-service")))
                .containsExactly("/api/hotels/**");
        assertThat(pathPatterns(routes.get("hotel-service-create-room")))
                .containsExactly("/api/rooms");
        assertThat(predicateValues(routes.get("hotel-service-create-room"), "Method"))
                .containsExactly("POST");
        assertThat(pathPatterns(routes.get("hotel-service-list-rooms")))
                .containsExactly("/api/rooms");
        assertThat(predicateValues(routes.get("hotel-service-list-rooms"), "Method"))
                .containsExactly("GET");
        assertThat(pathPatterns(routes.get("hotel-service-recommend-rooms")))
                .containsExactly("/api/rooms/recommend");
        assertThat(predicateValues(routes.get("hotel-service-recommend-rooms"), "Method"))
                .containsExactly("GET");
        assertRoute(routes.get("booking-service-create"), "/api/bookings", "POST", "/booking");
        assertRoute(routes.get("booking-service-history"), "/api/bookings", "GET", "/bookings");
        assertRoute(
                routes.get("booking-service-room-history"),
                "/api/bookings/rooms/{roomId}",
                "GET",
                "/bookings/rooms/{roomId}"
        );
        assertThat(pathPatterns(routes.get("booking-service-item")))
                .containsExactly("/api/bookings/{id}");
        assertThat(routes.get("booking-service-item").getFilters())
                .anySatisfy(filter -> {
                    assertThat(filter.getName()).isEqualTo("SetPath");
                    assertThat(filter.getArgs()).containsValue("/booking/{id}");
                });
    }

    private void assertRoute(
            RouteDefinition routeDefinition,
            String path,
            String method,
            String targetPath
    ) {
        assertThat(pathPatterns(routeDefinition)).containsExactly(path);
        assertThat(predicateValues(routeDefinition, "Method")).containsExactly(method);
        assertThat(routeDefinition.getFilters()).anySatisfy(filter -> {
            assertThat(filter.getName()).isEqualTo("SetPath");
            assertThat(filter.getArgs()).containsValue(targetPath);
        });
    }

    private List<String> pathPatterns(RouteDefinition routeDefinition) {
        return predicateValues(routeDefinition, "Path");
    }

    private List<String> predicateValues(RouteDefinition routeDefinition, String predicateName) {
        return routeDefinition.getPredicates().stream()
                .filter(predicate -> predicateName.equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .flatMap(value -> Arrays.stream(value.split(",")))
                .toList();
    }

    @Test
    void usesExpectedPort() {
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
    }
}
