package com.kseniia.hotelbooking.booking.controller;

import com.kseniia.hotelbooking.booking.service.BookingService;
import com.kseniia.hotelbooking.booking.service.UserAdministrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
class MethodSecurityTest {

    @Autowired
    private BookingController bookingController;

    @Autowired
    private UserAdministrationController userAdministrationController;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userCannotInvokeAdminRoomHistoryThroughControllerBean() {
        authenticateAs("USER");

        assertThatThrownBy(() -> bookingController.getRoomBookings(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userCannotInvokeUserAdministrationThroughControllerBean() {
        authenticateAs("USER");

        assertThatThrownBy(() -> userAdministrationController.create(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "test-user",
                null,
                "ROLE_" + role
        ));
    }
}
