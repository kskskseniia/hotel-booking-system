package com.kseniia.hotelbooking.booking.repository;

import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Booking> findAllByRoomIdOrderByStartDateAscIdAsc(Long roomId);

    List<Booking> findAllByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime cutoff);

    Optional<Booking> findByIdAndUser_Id(Long id, Long userId);
}
