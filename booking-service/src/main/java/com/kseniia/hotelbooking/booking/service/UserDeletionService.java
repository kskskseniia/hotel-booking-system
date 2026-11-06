package com.kseniia.hotelbooking.booking.service;

import org.springframework.stereotype.Service;

@Service
public class UserDeletionService {

    private final UserDeletionWorker worker;

    public UserDeletionService(UserDeletionWorker worker) {
        this.worker = worker;
    }

    public void delete(String username, String authorization) {
        resume(worker.prepare(username), authorization);
    }

    public void resume(Long userId, String authorization) {
        worker.findBookingIds(userId)
                .forEach(bookingId -> worker.releaseBooking(userId, bookingId, authorization));
        worker.finalizeDeletion(userId);
    }
}
