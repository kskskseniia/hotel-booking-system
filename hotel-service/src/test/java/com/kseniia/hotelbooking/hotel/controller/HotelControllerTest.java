package com.kseniia.hotelbooking.hotel.controller;

import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
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
import static org.hamcrest.Matchers.hasSize;
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
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @BeforeEach
    void clearHotels() {
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
                .andExpect(jsonPath("$.id").isNumber())
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
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(longName))
                .andExpect(jsonPath("$.address").value(longAddress));

        assertThat(hotelRepository.findAll())
                .singleElement()
                .extracting(Hotel::getName, Hotel::getAddress)
                .containsExactly(longName, longAddress);
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
                .andExpect(status().isBadRequest());

        assertThat(hotelRepository.count()).isZero();
    }

    @Test
    void listsHotels() throws Exception {
        Hotel aurora = hotelRepository.save(new Hotel("Aurora", "1 Central Street"));
        Hotel baltic = hotelRepository.save(new Hotel("Baltic", "2 River Road"));

        mockMvc.perform(get("/api/hotels").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d && @.name == 'Aurora' && @.address == '1 Central Street')]"
                        .formatted(aurora.getId()), hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == %d && @.name == 'Baltic' && @.address == '2 River Road')]"
                        .formatted(baltic.getId()), hasSize(1)));
    }

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
