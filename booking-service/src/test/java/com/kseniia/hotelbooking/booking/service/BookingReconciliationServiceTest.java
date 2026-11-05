package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.entity.Booking;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

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
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }
}
