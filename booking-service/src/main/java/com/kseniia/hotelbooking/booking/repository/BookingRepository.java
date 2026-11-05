package com.kseniia.hotelbooking.booking.repository;

import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Booking> findAllByRoomIdOrderByStartDateAscIdAsc(Long roomId);

    List<Booking> findAllByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime cutoff);
}
