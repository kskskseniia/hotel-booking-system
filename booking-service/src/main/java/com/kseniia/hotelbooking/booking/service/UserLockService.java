package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserLockService {

    private final UserRepository userRepository;

    public UserLockService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findByIdForUpdate(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameForUpdate(username);
    }
}
