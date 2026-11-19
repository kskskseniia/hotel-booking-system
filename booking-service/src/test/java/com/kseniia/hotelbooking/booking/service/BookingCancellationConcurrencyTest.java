package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.dto.CreateBookingRequest;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
class BookingCancellationConcurrencyTest {

    private static final LocalDate START_DATE = LocalDate.of(2099, 7, 10);
    private static final LocalDate END_DATE = LocalDate.of(2099, 7, 12);
    private static final String AUTHORIZATION = "Bearer test-token";

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private HotelClient hotelClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void compensatesHoldCreatedWhileCancellationIsInProgress() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("cancellation-race-user", "encoded-password", UserRole.USER)
        );
        CreateBookingRequest request = new CreateBookingRequest(
                "cancel-during-confirm",
                false,
                42L,
                START_DATE,
                END_DATE
        );
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        CountDownLatch allowConfirmation = new CountDownLatch(1);
        CountDownLatch cancellationReleaseStarted = new CountDownLatch(1);
        CountDownLatch allowCancellationRelease = new CountDownLatch(1);
        CountDownLatch compensationReleaseCompleted = new CountDownLatch(1);
        AtomicInteger releaseCalls = new AtomicInteger();
        doAnswer(invocation -> {
            confirmationStarted.countDown();
            assertThat(allowConfirmation.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(hotelClient).confirmAvailability(eq(42L), any(), eq(START_DATE), eq(END_DATE), any());
        doAnswer(invocation -> {
            if (releaseCalls.incrementAndGet() == 1) {
                cancellationReleaseStarted.countDown();
                assertThat(allowCancellationRelease.await(5, TimeUnit.SECONDS)).isTrue();
            } else {
                compensationReleaseCompleted.countDown();
            }
            return null;
        }).when(hotelClient).release(eq(42L), any(), any());

        try {
            Future<Object> creation = executor.submit(() -> {
                try {
                    return bookingService.create(request, user.getId(), AUTHORIZATION);
                } catch (RuntimeException exception) {
                    return exception;
                }
            });
            assertThat(confirmationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Booking booking = bookingRepository.findAll().getFirst();

            Future<Void> cancellation = executor.submit(() -> {
                bookingService.cancel(booking.getId(), user.getId(), AUTHORIZATION);
                return null;
            });
            assertThat(cancellationReleaseStarted.await(5, TimeUnit.SECONDS)).isTrue();

            allowConfirmation.countDown();
            assertThat(creation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(HotelServiceUnavailableException.class);
            assertThat(compensationReleaseCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            allowCancellationRelease.countDown();
            cancellation.get(5, TimeUnit.SECONDS);

            assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CANCELLED);
            verify(hotelClient, times(2)).release(42L, booking.getId(), AUTHORIZATION);
        } finally {
            allowConfirmation.countDown();
            allowCancellationRelease.countDown();
        }
    }

    @Test
    void failedConcurrentCancellationDoesNotUnblockConfirmation() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("concurrent-cancellation-user", "encoded-password", UserRole.USER)
        );
        CreateBookingRequest request = new CreateBookingRequest(
                "concurrent-cancel-during-confirm",
                false,
                42L,
                START_DATE,
                END_DATE
        );
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        CountDownLatch allowConfirmation = new CountDownLatch(1);
        CountDownLatch firstReleaseStarted = new CountDownLatch(1);
        CountDownLatch allowFirstReleaseFailure = new CountDownLatch(1);
        CountDownLatch secondReleaseStarted = new CountDownLatch(1);
        CountDownLatch allowSecondRelease = new CountDownLatch(1);
        CountDownLatch compensationReleaseCompleted = new CountDownLatch(1);
        AtomicInteger releaseCalls = new AtomicInteger();
        doAnswer(invocation -> {
            confirmationStarted.countDown();
            assertThat(allowConfirmation.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(hotelClient).confirmAvailability(eq(42L), any(), eq(START_DATE), eq(END_DATE), any());
        doAnswer(invocation -> switch (releaseCalls.incrementAndGet()) {
            case 1 -> {
                firstReleaseStarted.countDown();
                assertThat(allowFirstReleaseFailure.await(5, TimeUnit.SECONDS)).isTrue();
                throw new HotelServiceUnavailableException(new RuntimeException("first cancellation failed"));
            }
            case 2 -> {
                secondReleaseStarted.countDown();
                assertThat(allowSecondRelease.await(5, TimeUnit.SECONDS)).isTrue();
                yield null;
            }
            default -> {
                compensationReleaseCompleted.countDown();
                yield null;
            }
        }).when(hotelClient).release(eq(42L), any(), any());

        try {
            Future<Object> creation = executor.submit(() -> {
                try {
                    return bookingService.create(request, user.getId(), AUTHORIZATION);
                } catch (RuntimeException exception) {
                    return exception;
                }
            });
            assertThat(confirmationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Booking booking = bookingRepository.findAll().getFirst();

            Future<Object> firstCancellation = executor.submit(() -> {
                try {
                    bookingService.cancel(booking.getId(), user.getId(), AUTHORIZATION);
                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });
            assertThat(firstReleaseStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Void> secondCancellation = executor.submit(() -> {
                bookingService.cancel(booking.getId(), user.getId(), AUTHORIZATION);
                return null;
            });
            assertThat(secondReleaseStarted.await(5, TimeUnit.SECONDS)).isTrue();

            allowFirstReleaseFailure.countDown();
            assertThat(firstCancellation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(HotelServiceUnavailableException.class);

            allowConfirmation.countDown();
            assertThat(creation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(HotelServiceUnavailableException.class);
            assertThat(compensationReleaseCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            allowSecondRelease.countDown();
            secondCancellation.get(5, TimeUnit.SECONDS);

            assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CANCELLED);
            verify(hotelClient, times(3)).release(42L, booking.getId(), AUTHORIZATION);
        } finally {
            allowConfirmation.countDown();
            allowFirstReleaseFailure.countDown();
            allowSecondRelease.countDown();
        }
    }
}
