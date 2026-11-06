package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.dto.DeleteUserRequest;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!",
        "admin.username=admin",
        "admin.password=admin-password"
})
class UserDeletionConcurrencyTest {

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private BookingStateService bookingStateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private HotelClient hotelClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void deletionIntentRejectsNewBookingWhileReleaseIsInProgress() throws Exception {
        User user = userRepository.saveAndFlush(new User(
                "delete-race-user",
                passwordEncoder.encode("password"),
                UserRole.USER
        ));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                31L,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                BookingStatus.CONFIRMED,
                LocalDateTime.now()
        ));
        CountDownLatch releaseEntered = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);

        doAnswer(invocation -> {
            releaseEntered.countDown();
            assertThat(allowRelease.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(hotelClient).release(31L, booking.getId(), "Bearer admin");

        Future<?> deletion = executor.submit(() -> userAdministrationService.delete(
                new DeleteUserRequest("delete-race-user"),
                "Bearer admin"
        ));
        assertThat(releaseEntered.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> creation = executor.submit(() -> bookingStateService.createPending(
                user.getId(),
                32L,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(4)
        ));
        assertThatThrownBy(() -> creation.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> deletion.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeletionRequested()).isTrue();

        allowRelease.countDown();
        deletion.get(5, TimeUnit.SECONDS);
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(bookingRepository.findAll()).isEmpty();
    }
}
