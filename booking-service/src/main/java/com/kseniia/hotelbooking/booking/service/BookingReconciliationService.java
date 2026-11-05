package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class BookingReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingReconciliationService.class);

    private final BookingRepository bookingRepository;
    private final BookingReconciliationWorker reconciliationWorker;
    private final Duration pendingTimeout;

    public BookingReconciliationService(
            BookingRepository bookingRepository,
            BookingReconciliationWorker reconciliationWorker,
            @Value("${booking.pending-timeout:PT1M}") Duration pendingTimeout
    ) {
        this.bookingRepository = bookingRepository;
        this.reconciliationWorker = reconciliationWorker;
        this.pendingTimeout = pendingTimeout;
    }

    @Scheduled(
            fixedDelayString = "${booking.reconciliation-delay:30000}",
            initialDelayString = "${booking.reconciliation-initial-delay:60000}"
    )
    public void reconcilePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minus(pendingTimeout);
        bookingRepository.findAllByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)
                .forEach(this::reconcile);
    }

    private void reconcile(Booking booking) {
        try {
            if (reconciliationWorker.releaseAndCancelPending(booking.getId())) {
                LOGGER.info("Reconciled pending booking {} to CANCELLED", booking.getId());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to reconcile pending booking {}", booking.getId(), exception);
        }
    }
}
