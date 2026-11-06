package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingRequestStatus;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!",
        "booking.pending-timeout=1m",
        "booking.reconciliation-initial-delay=3600000"
})
class BookingReconciliationServiceTest {

    @Autowired
    private BookingReconciliationService reconciliationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingStateService bookingStateService;

    @MockitoBean
    private HotelClient hotelClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        reset(hotelClient);
    }

    @Test
    void releasesAndCancelsExpiredPendingBooking() {
        User user = userRepository.saveAndFlush(new User("reconciliation-user", "encoded", UserRole.USER));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                42L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                BookingStatus.PENDING,
                LocalDateTime.now().minusMinutes(5)
        ));

        reconciliationService.reconcilePendingBookings();

        verify(hotelClient).release(42L, booking.getId(), "");
        Booking reconciled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reconciled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reconciled.getRequestStatus()).isEqualTo(BookingRequestStatus.HOTEL_UNAVAILABLE);
    }

    @Test
    void cancelsExpiredAutoSelectCommandWithoutReleasingUnassignedRoom() {
        User user = userRepository.saveAndFlush(new User("unassigned-room-user", "encoded", UserRole.USER));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                "stale-auto-request",
                true,
                null,
                null,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                BookingStatus.PENDING,
                LocalDateTime.now().minusMinutes(5)
        ));

        reconciliationService.reconcilePendingBookings();

        verifyNoInteractions(hotelClient);
        Booking reconciled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reconciled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reconciled.getRequestStatus()).isEqualTo(BookingRequestStatus.HOTEL_UNAVAILABLE);
    }

    @Test
    void reconciliationSerializesWithConfirmation() throws Exception {
        User user = userRepository.saveAndFlush(new User("race-user", "encoded", UserRole.USER));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                42L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                BookingStatus.PENDING,
                LocalDateTime.now().minusMinutes(5)
        ));
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);
        CountDownLatch confirmationFinished = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseStarted.countDown();
            assertThat(allowRelease.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(hotelClient).release(42L, booking.getId(), "");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> reconciliation = executor.submit(reconciliationService::reconcilePendingBookings);
            assertThat(releaseStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> confirmation = executor.submit(() -> {
                try {
                    bookingStateService.confirm(booking.getId());
                } finally {
                    confirmationFinished.countDown();
                }
            });

            assertThat(confirmationFinished.await(300, TimeUnit.MILLISECONDS)).isFalse();
            allowRelease.countDown();
            reconciliation.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> confirmation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CANCELLED);
        } finally {
            allowRelease.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReconciliationReleasesPendingBookingOnce() throws Exception {
        User user = userRepository.saveAndFlush(new User("concurrent-user", "encoded", UserRole.USER));
        Booking booking = bookingRepository.saveAndFlush(new Booking(
                user,
                42L,
                LocalDate.of(2099, 7, 10),
                LocalDate.of(2099, 7, 12),
                BookingStatus.PENDING,
                LocalDateTime.now().minusMinutes(5)
        ));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> {
                await(start);
                reconciliationService.reconcilePendingBookings();
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                reconciliationService.reconcilePendingBookings();
            });
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            verify(hotelClient, times(1)).release(42L, booking.getId(), "");
            assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CANCELLED);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
