package com.kseniia.hotelbooking.booking.repository;

import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Booking> findAllByRoomIdOrderByStartDateAscIdAsc(Long roomId);

    List<Booking> findAllByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime cutoff);

    Optional<Booking> findByIdAndUser_Id(Long id, Long userId);

    Optional<Booking> findByUser_IdAndRequestId(Long userId, String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT booking FROM Booking booking WHERE booking.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
