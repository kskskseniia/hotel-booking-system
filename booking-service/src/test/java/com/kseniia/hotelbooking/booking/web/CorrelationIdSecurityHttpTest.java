package com.kseniia.hotelbooking.booking.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=correlation-security-test-secret-32-bytes!"
})
@AutoConfigureMockMvc
class CorrelationIdSecurityHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthorizedResponseKeepsIncomingCorrelationId() throws Exception {
        mockMvc.perform(get("/bookings")
                        .header(CorrelationIdFilter.HEADER_NAME, "booking-unauthorized-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        "booking-unauthorized-id"
                ));
    }
}
