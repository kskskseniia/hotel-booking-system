package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.entity.Room;
import com.kseniia.hotelbooking.hotel.entity.RoomHold;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomHoldRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=hotel-security-test-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomHoldRepository roomHoldRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearData() {
        roomHoldRepository.deleteAll();
        Integer roomTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'ROOMS'
                """, Integer.class);
        if (roomTableCount != null && roomTableCount > 0) {
            jdbcTemplate.update("DELETE FROM rooms");
        }
        hotelRepository.deleteAll();
    }

    @Test
    void createsRoomForHotel() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(post("/api/rooms")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "101"
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").isNumber())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.number").value("101"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));

        Map<String, Object> room = jdbcTemplate.queryForMap("""
                SELECT hotel_id, number, available, times_booked
                FROM rooms
                """);
        assertThat(room.get("HOTEL_ID")).isEqualTo(hotel.getId());
        assertThat(room.get("NUMBER")).isEqualTo("101");
        assertThat(room.get("AVAILABLE")).isEqualTo(true);
        assertThat(room.get("TIMES_BOOKED")).isEqualTo(0L);
    }

    @Test
    void updatesRoomAndPreservesTimesBooked() throws Exception {
        Hotel sourceHotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Hotel targetHotel = hotelRepository.save(new Hotel("Baltic", "2 River Road"));
        Room room = roomRepository.save(new Room(sourceHotel, "101", true, 7));

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "202",
                                  "available": false
                                }
                                """.formatted(targetHotel.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.hotelId").value(targetHotel.getId()))
                .andExpect(jsonPath("$.number").value("202"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.timesBooked").value(7));

        assertThat(roomRepository.findById(room.getId()))
                .get()
                .satisfies(updated -> {
                    assertThat(updated.getHotel().getId()).isEqualTo(targetHotel.getId());
                    assertThat(updated.getNumber()).isEqualTo("202");
                    assertThat(updated.isAvailable()).isFalse();
                    assertThat(updated.getTimesBooked()).isEqualTo(7);
                });
    }

    @Test
    void rejectsInvalidRoomUpdateWithoutChangingRoom() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Room room = roomRepository.save(new Room(hotel, "101", true, 7));

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": " "
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isBadRequest());

        assertThat(roomRepository.findById(room.getId()))
                .get()
                .satisfies(unchanged -> {
                    assertThat(unchanged.getHotel().getId()).isEqualTo(hotel.getId());
                    assertThat(unchanged.getNumber()).isEqualTo("101");
                    assertThat(unchanged.isAvailable()).isTrue();
                    assertThat(unchanged.getTimesBooked()).isEqualTo(7);
                });
    }

    @Test
    void rejectsUpdatingRoomToUnknownHotelWithoutChangingRoom() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Room room = roomRepository.save(new Room(hotel, "101", true, 7));

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": 999999,
                                  "number": "202",
                                  "available": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hotel not found"));

        assertThat(roomRepository.findById(room.getId()))
                .get()
                .satisfies(unchanged -> {
                    assertThat(unchanged.getHotel().getId()).isEqualTo(hotel.getId());
                    assertThat(unchanged.getNumber()).isEqualTo("101");
                    assertThat(unchanged.isAvailable()).isTrue();
                    assertThat(unchanged.getTimesBooked()).isEqualTo(7);
                });
    }

    @Test
    void rejectsUpdatingUnknownRoom() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(put("/api/rooms/{id}", 999999)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": "202",
                                  "available": false
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Room not found"));
    }

    @Test
    void deletesRoomWithoutHold() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Room room = roomRepository.save(new Room(hotel, "101"));

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(roomRepository.existsById(room.getId())).isFalse();
    }

    @Test
    void rejectsDeletingUnknownRoom() throws Exception {
        mockMvc.perform(delete("/api/rooms/{id}", 999999).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Room not found"));
    }

    @Test
    void rejectsDeletingRoomWithActiveHold() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Room room = roomRepository.save(new Room(hotel, "101"));
        roomHoldRepository.save(new RoomHold(
                room,
                9001L,
                LocalDate.of(2030, 1, 10),
                LocalDate.of(2030, 1, 12)
        ));

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Room still has an active hold"));

        assertThat(roomRepository.existsById(room.getId())).isTrue();
        assertThat(roomHoldRepository.findByBookingId(9001L)).isPresent();
    }

    @Test
    void rejectsUnknownHotel() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": 999999,
                                  "number": "101"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Hotel not found"))
                .andExpect(jsonPath("$.path").value("/api/rooms"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        Long roomCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Long.class);
        assertThat(roomCount).isZero();
    }

    @Test
    void rejectsBlankRoomNumber() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(post("/api/rooms")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hotelId": %d,
                                  "number": " "
                                }
                                """.formatted(hotel.getId())))
                .andExpect(status().isBadRequest());

        Long roomCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Long.class);
        assertThat(roomCount).isZero();
    }

    @Test
    void rejectsMissingHotelId() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number": "101"
                                }
                                """))
                .andExpect(status().isBadRequest());

        Long roomCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Long.class);
        assertThat(roomCount).isZero();
    }

    @Test
    void returnsOnlyAvailableRooms() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "101", true, 0L);
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "102", false, 3L);

        mockMvc.perform(get("/api/rooms").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roomId").isNumber())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$[0].number").value("101"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].timesBooked").value(0));
    }

    @Test
    void recommendsAvailableRoomsByTimesBookedThenId() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "301", true, 2L);
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "302", true, 0L);
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "303", true, 0L);
        jdbcTemplate.update("""
                INSERT INTO rooms (hotel_id, number, available, times_booked)
                VALUES (?, ?, ?, ?)
                """, hotel.getId(), "304", false, 0L);

        mockMvc.perform(get("/api/rooms/recommend").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].number").value("302"))
                .andExpect(jsonPath("$[1].number").value("303"))
                .andExpect(jsonPath("$[2].number").value("301"));
    }

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
