package com.kseniia.hotelbooking.hotel.repository;

import com.kseniia.hotelbooking.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByAvailableTrue();

    List<Room> findAllByAvailableTrueOrderByTimesBookedAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM Room room WHERE room.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
