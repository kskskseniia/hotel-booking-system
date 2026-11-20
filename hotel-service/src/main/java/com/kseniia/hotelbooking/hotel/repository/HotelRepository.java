package com.kseniia.hotelbooking.hotel.repository;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT hotel FROM Hotel hotel WHERE hotel.id = :id")
    Optional<Hotel> findByIdForUpdate(@Param("id") Long id);
}
