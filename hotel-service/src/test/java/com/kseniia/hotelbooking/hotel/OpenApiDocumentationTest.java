package com.kseniia.hotelbooking.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = HotelServiceApplication.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsHotelServiceApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/hotels'].get").exists())
                .andExpect(jsonPath("$.paths['/api/hotels'].post").exists())
                .andExpect(jsonPath("$.paths['/api/rooms'].post").exists());
    }
}
