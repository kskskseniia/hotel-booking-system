package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.entity.Room;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=hotel-security-test-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class RoomAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearData() {
        Integer holdTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'ROOM_HOLDS'
                """, Integer.class);
        if (holdTableCount != null && holdTableCount > 0) {
            jdbcTemplate.update("DELETE FROM room_holds");
        }
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void confirmsRoomAvailabilityForBooking() throws Exception {
        Room room = createRoom();

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 1001,
                                  "startDate": "2099-06-01",
                                  "endDate": "2099-06-05"
                                }
                                """))
                .andExpect(status().isNoContent());

        Long holdCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class);
        Long timesBooked = jdbcTemplate.queryForObject(
                "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId());
        assertThat(holdCount).isEqualTo(1L);
        assertThat(timesBooked).isEqualTo(1L);
    }

    @Test
    void repeatedConfirmationIsIdempotent() throws Exception {
        Room room = createRoom();
        String request = """
                {
                  "bookingId": 1001,
                  "startDate": "2099-06-01",
                  "endDate": "2099-06-05"
                }
                """;

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isNoContent());
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId())).isEqualTo(1L);
    }

    @Test
    void rejectsOverlappingRoomHold() throws Exception {
        Room room = createRoom();

        confirm(room, 1001, "2099-06-01", "2099-06-05")
                .andExpect(status().isNoContent());
        confirm(room, 1002, "2099-06-04", "2099-06-08")
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId())).isEqualTo(1L);
    }

    @Test
    void releasesRoomHoldIdempotently() throws Exception {
        Room room = createRoom();
        confirm(room, 1001, "2099-06-01", "2099-06-05")
                .andExpect(status().isNoContent());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/rooms/{id}/release", room.getId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "bookingId": 1001
                                    }
                                    """))
                    .andExpect(status().isNoContent());
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId())).isZero();
    }

    @Test
    void rejectsInvalidDateRange() throws Exception {
        Room room = createRoom();

        confirm(room, 1001, "2099-06-05", "2099-06-05")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", 999999)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 1001,
                                  "startDate": "2099-06-01",
                                  "endDate": "2099-06-05"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedConfirmation() throws Exception {
        Room room = createRoom();

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 1001,
                                  "startDate": "2099-06-01",
                                  "endDate": "2099-06-05"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserConfirmation() throws Exception {
        Room room = createRoom();

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 1001,
                                  "startDate": "2099-06-01",
                                  "endDate": "2099-06-05"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentOverlappingHolds() throws Exception {
        Room room = createRoom();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() -> concurrentConfirmation(room, 1001, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentConfirmation(room, 1002, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(new int[]{
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            }).containsExactlyInAnyOrder(204, 409);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRepeatedReleaseDecrementsCounterOnlyOnce() throws Exception {
        Room room = createRoom();
        confirm(room, 1001, "2099-06-01", "2099-06-05").andExpect(status().isNoContent());
        confirm(room, 1002, "2099-06-10", "2099-06-15").andExpect(status().isNoContent());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() -> concurrentRelease(room, 1001, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentRelease(room, 1001, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(new int[]{
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            }).containsOnly(204);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_holds", Long.class)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT times_booked FROM rooms WHERE id = ?", Long.class, room.getId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private int concurrentConfirmation(
            Room room,
            long bookingId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent confirmation did not start in time");
        }
        return confirm(room, bookingId, "2099-07-01", "2099-07-05")
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int concurrentRelease(
            Room room,
            long bookingId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent release did not start in time");
        }
        return release(room, bookingId)
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions confirm(
            Room room,
            long bookingId,
            String startDate,
            String endDate
    ) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "bookingId": %d,
                          "startDate": "%s",
                          "endDate": "%s"
                        }
                        """.formatted(bookingId, startDate, endDate)));
    }

    private org.springframework.test.web.servlet.ResultActions release(Room room, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", room.getId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "bookingId": %d
                        }
                        """.formatted(bookingId)));
    }

    private Room createRoom() {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        return roomRepository.save(new Room(hotel, "101"));
    }
}
