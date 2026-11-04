package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.CreateUserRequest;
import com.kseniia.hotelbooking.booking.dto.UserResponse;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.exception.UsernameAlreadyExistsException;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role()
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new UsernameAlreadyExistsException();
        }
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
