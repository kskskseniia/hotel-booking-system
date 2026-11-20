package com.kseniia.hotelbooking.booking.config;

import com.kseniia.hotelbooking.booking.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HotelClientConfigurationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesCorrelationIdFromMdc() {
        RestClient.Builder builder = new HotelClientConfiguration().hotelRestClientBuilder(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://hotel-service/hotels"))
                .andExpect(header(CorrelationIdFilter.HEADER_NAME, "booking-to-hotel-id"))
                .andRespond(withSuccess());
        MDC.put(CorrelationIdFilter.MDC_KEY, "booking-to-hotel-id");

        builder.build().get()
                .uri("http://hotel-service/hotels")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }
}
