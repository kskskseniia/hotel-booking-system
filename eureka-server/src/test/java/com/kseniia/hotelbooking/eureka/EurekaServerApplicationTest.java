package com.kseniia.hotelbooking.eureka;

import com.netflix.eureka.EurekaServerContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EurekaServerApplication.class)
class EurekaServerApplicationTest {

    @Autowired(required = false)
    private EurekaServerContext eurekaServerContext;

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void startsEurekaServerInfrastructure() {
        assertThat(eurekaServerContext).isNotNull();
    }

    @Test
    void bindsOnlyToLoopbackInterface() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}
