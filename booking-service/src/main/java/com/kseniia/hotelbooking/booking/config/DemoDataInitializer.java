package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    private static final String USERNAME = "demo-user";
    private static final String USER_PASSWORD = "DemoUser123!";
    private static final String ADMIN_USERNAME = "demo-admin";
    private static final String ADMIN_PASSWORD = "DemoAdmin123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    DemoDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        createUserIfMissing(USERNAME, USER_PASSWORD, UserRole.USER);
        createUserIfMissing(ADMIN_USERNAME, ADMIN_PASSWORD, UserRole.ADMIN);
    }

    private void createUserIfMissing(String username, String password, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(password), role));
    }
}