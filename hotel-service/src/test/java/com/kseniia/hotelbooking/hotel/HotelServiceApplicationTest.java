package com.kseniia.hotelbooking.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HotelServiceApplication.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
class HotelServiceApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesExpectedLocalConfiguration() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("hotel-service");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8081);
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:hoteldb");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }
}