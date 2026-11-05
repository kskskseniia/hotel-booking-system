package com.kseniia.hotelbooking.hotel.repository;

import com.kseniia.hotelbooking.hotel.entity.RoomHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RoomHoldRepository extends JpaRepository<RoomHold, Long> {

    Optional<RoomHold> findByBookingId(Long bookingId);

    boolean existsByRoomIdAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId,
            LocalDate endDate,
            LocalDate startDate
    );
}
