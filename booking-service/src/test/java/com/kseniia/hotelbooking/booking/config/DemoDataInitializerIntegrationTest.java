package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:booking-demo-data-test",
        "jwt.secret=demo-data-test-jwt-secret-at-least-32-bytes"
})
@ActiveProfiles("demo")
class DemoDataInitializerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsDemoUsersWithBcryptPasswords() {
        User user = userRepository.findByUsername("demo-user").orElseThrow();
        User admin = userRepository.findByUsername("demo-admin").orElseThrow();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(passwordEncoder.matches("DemoUser123!", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("DemoUser123!");

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwordEncoder.matches("DemoAdmin123!", admin.getPasswordHash())).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo("DemoAdmin123!");
    }
}