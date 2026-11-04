package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsMissingAdminWithEncodedPassword() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-local-admin-password")).thenReturn("bcrypt-hash");

        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "admin",
                "a-long-local-admin-password"
        );

        bootstrap.run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User createdAdmin = userCaptor.getValue();
        assertThat(createdAdmin.getUsername()).isEqualTo("admin");
        assertThat(createdAdmin.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(createdAdmin.getPasswordHash()).doesNotContain("a-long-local-admin-password");
        assertThat(createdAdmin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsBootstrapUsernameOwnedByRegularUser() {
        User regularUser = new User("admin", "existing-hash", UserRole.USER);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(regularUser));

        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "admin",
                "a-long-local-admin-password"
        );

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_USERNAME already belongs to a non-admin user");
    }

    @Test
    void rejectsBlankBootstrapUsernameBeforeAccessingRepository() {
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                " ",
                "a-long-local-admin-password"
        );

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_USERNAME must not be blank");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void rejectsBootstrapPasswordOutsideBcryptLimits() {
        AdminBootstrap shortPasswordBootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "admin",
                "too-short"
        );
        AdminBootstrap longPasswordBootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "admin",
                "a".repeat(73)
        );

        assertThatThrownBy(() -> shortPasswordBootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_PASSWORD must contain between 12 and 72 UTF-8 bytes");
        assertThatThrownBy(() -> longPasswordBootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_PASSWORD must contain between 12 and 72 UTF-8 bytes");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenBootstrapIsDisabled() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                false,
                "",
                ""
        );

        bootstrap.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNotOverwriteExistingAdmin() throws Exception {
        User existingAdmin = new User("admin", "existing-hash", UserRole.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingAdmin));

        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "admin",
                "a-long-local-admin-password"
        );

        bootstrap.run(null);

        verify(userRepository).findByUsername("admin");
        verifyNoMoreInteractions(userRepository, passwordEncoder);
    }
}
