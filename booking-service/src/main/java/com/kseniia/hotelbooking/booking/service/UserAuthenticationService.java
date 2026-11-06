package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.CredentialsRequest;
import com.kseniia.hotelbooking.booking.dto.TokenResponse;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.exception.InvalidCredentialsException;
import com.kseniia.hotelbooking.booking.exception.UsernameAlreadyExistsException;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthenticationService {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String dummyPasswordHash;

    public UserAuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-value");
    }

    @Transactional
    public TokenResponse register(CredentialsRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                UserRole.USER
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isUsernameUniqueViolation(exception)) {
                throw new UsernameAlreadyExistsException();
            }
            throw exception;
        }
        return jwtTokenService.issueToken(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse authenticate(CredentialsRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || user.isDeletionRequested() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return jwtTokenService.issueToken(user);
    }

    private boolean isUsernameUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                return constraintName != null
                        && constraintName.toLowerCase().contains(USERNAME_UNIQUE_CONSTRAINT);
            }
            cause = cause.getCause();
        }
        return false;
    }
}
