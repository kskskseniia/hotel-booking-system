package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String username;
    private final String password;

    AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${admin.bootstrap.username:}") String username,
            @Value("${admin.bootstrap.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME must not be blank");
        }
        int passwordBytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 12 || passwordBytes > 72) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must contain between 12 and 72 UTF-8 bytes"
            );
        }

        User existingUser = userRepository.findByUsername(username).orElse(null);
        if (existingUser != null) {
            if (existingUser.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("ADMIN_USERNAME already belongs to a non-admin user");
            }
            return;
        }

        User admin = new User(username, passwordEncoder.encode(password), UserRole.ADMIN);
        userRepository.saveAndFlush(admin);
    }
}
