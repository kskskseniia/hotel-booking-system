package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.dto.CredentialsRequest;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.exception.InvalidCredentialsException;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private UserAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new UserAuthenticationService(
                userRepository,
                passwordEncoder,
                jwtTokenService
        );
    }

    @Test
    void doesNotMapUnrelatedDatabaseFailureToDuplicateUsername() {
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException("unrelated failure");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("password-hash");
        when(userRepository.saveAndFlush(any())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> authenticationService.register(
                new CredentialsRequest("alice", "strong-password")
        )).isSameAs(databaseFailure);
    }

    @Test
    void rejectsAuthenticationWhileDeletionIsPending() {
        User user = new User("alice", "password-hash", UserRole.USER);
        user.requestDeletion();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("strong-password", "password-hash")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.authenticate(
                new CredentialsRequest("alice", "strong-password")
        )).isInstanceOf(InvalidCredentialsException.class);
        verify(jwtTokenService, never()).issueToken(any());
    }
}
