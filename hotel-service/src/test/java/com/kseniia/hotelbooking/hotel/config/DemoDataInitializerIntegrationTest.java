package com.kseniia.hotelbooking.hotel.config;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.entity.Room;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:hotel-demo-data-test",
        "jwt.secret=demo-data-test-jwt-secret-at-least-32-bytes"
})
@ActiveProfiles("demo")
class DemoDataInitializerIntegrationTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @Transactional
    void createsHotelsAndRoomsForDemoScenarios() {
        Map<String, Hotel> hotels = hotelRepository.findAll().stream()
                .collect(toMap(Hotel::getName, identity()));
        Map<String, Map<String, Room>> roomsByHotel = roomRepository.findAll().stream()
                .collect(groupingBy(
                        room -> room.getHotel().getName(),
                        toMap(Room::getNumber, identity())
                ));

        assertThat(hotels).containsOnlyKeys("Москва", "Санкт-Петербург");
        assertThat(hotels.get("Москва").getAddress()).isEqualTo("Москва, ул. Тверская, 10");
        assertThat(hotels.get("Санкт-Петербург").getAddress())
                .isEqualTo("Санкт-Петербург, Невский проспект, 25");

        assertThat(roomsByHotel).containsOnlyKeys("Москва", "Санкт-Петербург");
        assertThat(roomsByHotel.get("Москва")).containsOnlyKeys("101", "102", "103");
        assertThat(roomsByHotel.get("Санкт-Петербург")).containsOnlyKeys("101", "102");
        assertRoom(roomsByHotel.get("Москва").get("101"), "Москва", true, 0);
        assertRoom(roomsByHotel.get("Москва").get("102"), "Москва", true, 2);
        assertRoom(roomsByHotel.get("Москва").get("103"), "Москва", false, 0);
        assertRoom(roomsByHotel.get("Санкт-Петербург").get("101"), "Санкт-Петербург", true, 1);
        assertRoom(roomsByHotel.get("Санкт-Петербург").get("102"), "Санкт-Петербург", true, 3);
    }

    private static void assertRoom(
            Room room,
            String hotelName,
            boolean available,
            long timesBooked
    ) {
        assertThat(room.getHotel().getName()).isEqualTo(hotelName);
        assertThat(room.isAvailable()).isEqualTo(available);
        assertThat(room.getTimesBooked()).isEqualTo(timesBooked);
    }
}