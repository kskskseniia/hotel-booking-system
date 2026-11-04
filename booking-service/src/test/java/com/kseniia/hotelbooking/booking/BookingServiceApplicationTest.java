package com.kseniia.hotelbooking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = BookingServiceApplication.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
        }
)
class BookingServiceApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesExpectedLocalConfiguration() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("booking-service");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8082);
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:bookingdb");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }
}
