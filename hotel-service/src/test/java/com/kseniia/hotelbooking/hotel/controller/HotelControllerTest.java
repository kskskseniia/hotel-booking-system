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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=hotel-security-test-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void clearHotels() {
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void createsHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Aurora",
                                  "address": "1 Central Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").isNumber())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.name").value("Aurora"))
                .andExpect(jsonPath("$.address").value("1 Central Street"));

        assertThat(hotelRepository.findAll())
                .singleElement()
                .extracting("name", "address")
                .containsExactly("Aurora", "1 Central Street");
    }

    @Test
    void createsHotelWithFieldsLongerThan255Characters() throws Exception {
        String longName = "N".repeat(300);
        String longAddress = "A".repeat(300);

        mockMvc.perform(post("/api/hotels")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "%s"
                                }
                                """.formatted(longName, longAddress)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").isNumber())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.name").value(longName))
                .andExpect(jsonPath("$.address").value(longAddress));

        assertThat(hotelRepository.findAll())
                .singleElement()
                .extracting(Hotel::getName, Hotel::getAddress)
                .containsExactly(longName, longAddress);
    }

    @Test
    void updatesHotel() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(put("/api/hotels/{id}", hotel.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Aurora Premium",
                                  "address": "2 Central Street"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.name").value("Aurora Premium"))
                .andExpect(jsonPath("$.address").value("2 Central Street"));

        assertThat(hotelRepository.findById(hotel.getId()))
                .get()
                .extracting(Hotel::getName, Hotel::getAddress)
                .containsExactly("Aurora Premium", "2 Central Street");
    }

    @Test
    void rejectsInvalidHotelUpdateWithoutChangingHotel() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(put("/api/hotels/{id}", hotel.getId())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "address": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(hotelRepository.findById(hotel.getId()))
                .get()
                .extracting(Hotel::getName, Hotel::getAddress)
                .containsExactly("Aurora", "1 Central Street");
    }

    @Test
    void rejectsUpdatingUnknownHotel() throws Exception {
        mockMvc.perform(put("/api/hotels/{id}", 999999)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Aurora",
                                  "address": "1 Central Street"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hotel not found"));
    }

    @Test
    void deletesHotelWithoutRooms() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(hotelRepository.existsById(hotel.getId())).isFalse();
    }

    @Test
    void rejectsDeletingUnknownHotel() throws Exception {
        mockMvc.perform(delete("/api/hotels/{id}", 999999).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hotel not found"));
    }

    @Test
    void rejectsDeletingHotelWithRooms() throws Exception {
        Hotel hotel = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Room room = roomRepository.save(new Room(hotel, "101"));

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Hotel still has rooms"));

        assertThat(hotelRepository.existsById(hotel.getId())).isTrue();
        assertThat(roomRepository.existsById(room.getId())).isTrue();
    }

    @Test
    void rejectsBlankHotelFields() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "address": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/hotels"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(hotelRepository.count()).isZero();
    }

    @Test
    void listsHotels() throws Exception {
        Hotel aurora = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Hotel baltic = hotelRepository.save(new Hotel("Baltic", "2 River Road"));

        mockMvc.perform(get("/api/hotels").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.hotelId == %d && @.name == 'Aurora' && @.address == '1 Central Street')]"
                        .formatted(aurora.getId()), hasSize(1)))
                .andExpect(jsonPath("$[?(@.hotelId == %d && @.name == 'Baltic' && @.address == '2 River Road')]"
                        .formatted(baltic.getId()), hasSize(1)));
    }

    @Test
    void preservesMethodNotAllowedStatusAndHeader() throws Exception {
        mockMvc.perform(delete("/api/hotels").with(admin()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void preservesUnsupportedMediaTypeStatus() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .with(admin())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
