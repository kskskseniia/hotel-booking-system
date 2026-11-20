package com.kseniia.hotelbooking.hotel.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void exposesIncomingCorrelationIdDuringRequestAndClearsItAfterwards() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "hotel-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdDuringRequest = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                correlationIdDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(correlationIdDuringRequest).hasValue("hotel-correlation-id");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("hotel-correlation-id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenRequestDoesNotContainOne() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdDuringRequest = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                correlationIdDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String generatedCorrelationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generatedCorrelationId).isNotBlank();
        assertThat(UUID.fromString(generatedCorrelationId)).isNotNull();
        assertThat(correlationIdDuringRequest).hasValue(generatedCorrelationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
