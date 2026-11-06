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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
class UserDeletionRecoveryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserDeletionService userDeletionService;

    @Autowired
    private UserDeletionReconciliationService reconciliationService;

    @MockitoSpyBean
    private UserDeletionWorker userDeletionWorker;

    @MockitoBean
    private HotelClient hotelClient;

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reconcilerCompletesDurableDeletion() {
        User user = deletionRequestedUser("reconcile-delete");
        Booking booking = confirmedBooking(user, 41L);

        reconciliationService.reconcile();

        verify(hotelClient).release(41L, booking.getId(), "");
        assertThat(bookingRepository.findById(booking.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void localCommitFailureAfterReleaseRemainsRetryable() {
        User user = deletionRequestedUser("commit-failure-delete");
        Booking booking = confirmedBooking(user, 42L);
        AtomicBoolean failCommit = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (failCommit.getAndSet(false)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("test commit failure");
                    }
                });
            }
            return null;
        }).when(userDeletionWorker).releaseBooking(user.getId(), booking.getId(), "");

        assertThatThrownBy(() -> userDeletionService.resume(user.getId(), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test commit failure");
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeletionRequested()).isTrue();

        userDeletionService.resume(user.getId(), "");

        verify(hotelClient, times(2)).release(42L, booking.getId(), "");
        assertThat(bookingRepository.findById(booking.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    private User deletionRequestedUser(String username) {
        User user = new User(username, "password-hash", UserRole.USER);
        user.requestDeletion();
        return userRepository.saveAndFlush(user);
    }

    private Booking confirmedBooking(User user, Long roomId) {
        return bookingRepository.saveAndFlush(new Booking(
                user,
                roomId,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                BookingStatus.CONFIRMED,
                LocalDateTime.now()
        ));
    }
}
