package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearData() {
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
                .andExpect(jsonPath("$.id").isNumber())
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
                .andExpect(status().isNotFound());

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
