package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
class BookingIdempotencyConcurrencyTest {

    @Autowired
    private BookingStateService bookingStateService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

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
    void concurrentCreationWithSameRequestIdReturnsSingleBooking() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("idempotency-user", "encoded-password", UserRole.USER)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<BookingStateService.PendingBookingResult> first = submitCreation(user.getId(), ready, start);
        Future<BookingStateService.PendingBookingResult> second = submitCreation(user.getId(), ready, start);

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        BookingStateService.PendingBookingResult firstResult = first.get(5, TimeUnit.SECONDS);
        BookingStateService.PendingBookingResult secondResult = second.get(5, TimeUnit.SECONDS);

        assertThat(firstResult.booking().id()).isEqualTo(secondResult.booking().id());
        assertThat(List.of(firstResult.created(), secondResult.created()))
                .containsExactlyInAnyOrder(true, false);
        assertThat(bookingRepository.count()).isOne();
    }

    private Future<BookingStateService.PendingBookingResult> submitCreation(
            Long userId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return bookingStateService.createPending(
                    userId,
                    "concurrent-request-1",
                    false,
                    42L,
                    42L,
                    LocalDate.of(2099, 7, 10),
                    LocalDate.of(2099, 7, 12)
            );
        });
    }
}
