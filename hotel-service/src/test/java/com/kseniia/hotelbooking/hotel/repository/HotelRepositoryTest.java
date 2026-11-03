package com.kseniia.hotelbooking.hotel.repository;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HotelRepositoryTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsHotel() {
        Hotel savedHotel = hotelRepository.saveAndFlush(new Hotel("Aurora", "1 Central Street"));
        entityManager.clear();

        assertThat(savedHotel.getId()).isNotNull();
        assertThat(hotelRepository.findById(savedHotel.getId()))
                .get()
                .extracting(Hotel::getName, Hotel::getAddress)
                .containsExactly("Aurora", "1 Central Street");
    }
}
