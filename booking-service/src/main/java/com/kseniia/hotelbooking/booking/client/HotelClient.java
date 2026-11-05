package com.kseniia.hotelbooking.booking.client;

import java.time.LocalDate;
import java.util.List;

public interface HotelClient {

    List<RoomCandidate> getRecommendedRooms(String authorization);

    void confirmAvailability(
            Long roomId,
            Long bookingId,
            LocalDate startDate,
            LocalDate endDate,
            String authorization
    );

    void release(Long roomId, Long bookingId, String authorization);
}
