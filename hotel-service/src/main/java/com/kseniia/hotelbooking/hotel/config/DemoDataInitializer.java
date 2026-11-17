package com.kseniia.hotelbooking.hotel.config;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.entity.Room;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    DemoDataInitializer(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (hotelRepository.count() > 0 || roomRepository.count() > 0) {
            return;
        }

        Hotel moscow = hotelRepository.save(new Hotel("Москва", "Москва, ул. Тверская, 10"));
        Hotel saintPetersburg = hotelRepository.save(
                new Hotel("Санкт-Петербург", "Санкт-Петербург, Невский проспект, 25")
        );

        roomRepository.save(new Room(moscow, "101", true, 0));
        roomRepository.save(new Room(moscow, "102", true, 2));
        roomRepository.save(new Room(moscow, "103", false, 0));
        roomRepository.save(new Room(saintPetersburg, "101", true, 1));
        roomRepository.save(new Room(saintPetersburg, "102", true, 3));
    }
}