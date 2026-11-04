package com.kseniia.hotelbooking.booking.repository;

import com.kseniia.hotelbooking.booking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
