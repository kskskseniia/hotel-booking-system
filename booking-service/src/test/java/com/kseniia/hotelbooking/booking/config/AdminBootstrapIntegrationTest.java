package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admin-bootstrap-test;DB_CLOSE_DELAY=-1",
        "jwt.secret=integration-test-jwt-secret-at-least-32-bytes",
        "admin.bootstrap.enabled=true",
        "admin.bootstrap.username=integration-admin",
        "admin.bootstrap.password=integration-admin-password"
})
class AdminBootstrapIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminFromApplicationPropertiesWithBcryptPassword() {
        User admin = userRepository.findByUsername("integration-admin").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo("integration-admin-password");
        assertThat(passwordEncoder.matches("integration-admin-password", admin.getPasswordHash())).isTrue();
    }
}
