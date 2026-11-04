package com.kseniia.hotelbooking.booking.repository;

import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookingRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesUserWithRequiredFields() {
        User savedUser = userRepository.saveAndFlush(
                new User("alice", "encoded-password", UserRole.USER)
        );
        entityManager.clear();

        assertThat(savedUser.getId()).isNotNull();
        assertThat(userRepository.findById(savedUser.getId()))
                .get()
                .satisfies(user -> {
                    assertThat(user.getUsername()).isEqualTo("alice");
                    assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
                    assertThat(user.getRole()).isEqualTo(UserRole.USER);
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?",
                String.class,
                savedUser.getId()
        )).isEqualTo("USER");
    }

    @Test
    void savesBookingWithRequiredFields() {
        User user = userRepository.saveAndFlush(
                new User("alice", "encoded-password", UserRole.USER)
        );
        LocalDate startDate = LocalDate.of(2026, 11, 10);
        LocalDate endDate = LocalDate.of(2026, 11, 12);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 9, 0);

        Booking savedBooking = bookingRepository.saveAndFlush(
                new Booking(user, 42L, startDate, endDate, BookingStatus.PENDING, createdAt)
        );
        entityManager.clear();

        assertThat(savedBooking.getId()).isNotNull();
        assertThat(bookingRepository.findById(savedBooking.getId()))
                .get()
                .satisfies(booking -> {
                    assertThat(booking.getUser().getId()).isEqualTo(user.getId());
                    assertThat(booking.getRoomId()).isEqualTo(42L);
                    assertThat(booking.getStartDate()).isEqualTo(startDate);
                    assertThat(booking.getEndDate()).isEqualTo(endDate);
                    assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
                    assertThat(booking.getCreatedAt()).isEqualTo(createdAt);
                });
    }
}
