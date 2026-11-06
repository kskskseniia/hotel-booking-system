package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.CreateUserRequest;
import com.kseniia.hotelbooking.booking.dto.DeleteUserRequest;
import com.kseniia.hotelbooking.booking.dto.UpdateUserRequest;
import com.kseniia.hotelbooking.booking.dto.UserResponse;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.exception.UserNotFoundException;
import com.kseniia.hotelbooking.booking.exception.UsernameAlreadyExistsException;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final UserLockService userLockService;
    private final PasswordEncoder passwordEncoder;
    private final UserDeletionService userDeletionService;

    public UserAdministrationService(
            UserRepository userRepository,
            UserLockService userLockService,
            PasswordEncoder passwordEncoder,
            UserDeletionService userDeletionService
    ) {
        this.userRepository = userRepository;
        this.userLockService = userLockService;
        this.passwordEncoder = passwordEncoder;
        this.userDeletionService = userDeletionService;
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

    @Transactional
    public UserResponse update(UpdateUserRequest request) {
        User user = userLockService.findByUsername(request.username())
                .orElseThrow(UserNotFoundException::new);
        if (user.isDeletionRequested()) {
            throw new UserNotFoundException();
        }
        user.updateRole(request.role());
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    public void delete(DeleteUserRequest request, String authorization) {
        userDeletionService.delete(request.username(), authorization);
    }
}
