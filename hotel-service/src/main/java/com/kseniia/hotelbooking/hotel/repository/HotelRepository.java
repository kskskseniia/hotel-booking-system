package com.kseniia.hotelbooking.hotel.repository;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
