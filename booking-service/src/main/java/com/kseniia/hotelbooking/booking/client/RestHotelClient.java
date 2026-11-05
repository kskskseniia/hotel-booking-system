package com.kseniia.hotelbooking.booking.client;

import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import com.kseniia.hotelbooking.booking.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Component
public class RestHotelClient implements HotelClient {

    private final RestClient restClient;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final JwtTokenService jwtTokenService;

    public RestHotelClient(
            RestClient.Builder hotelRestClientBuilder,
            @Value("${hotel-service.base-url:http://hotel-service}") String baseUrl,
            @Value("${hotel-service.max-attempts:3}") int maxAttempts,
            @Value("${hotel-service.retry-backoff:100ms}") Duration retryBackoff,
            JwtTokenService jwtTokenService
    ) {
        this.restClient = hotelRestClientBuilder.baseUrl(baseUrl).build();
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public List<RoomCandidate> getRecommendedRooms(String authorization) {
        RoomCandidate[] rooms = execute(() -> restClient.get()
                .uri("/api/rooms/recommend")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(RoomCandidate[].class), false);
        return rooms == null ? List.of() : Arrays.asList(rooms);
    }

    @Override
    public void confirmAvailability(
            Long roomId,
            Long bookingId,
            LocalDate startDate,
            LocalDate endDate,
            String authorization
    ) {
        execute(() -> {
            restClient.post()
                    .uri("/api/rooms/{roomId}/confirm-availability", roomId)
                    .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                    .body(new ConfirmAvailabilityRequest(
                            bookingId,
                            startDate.toString(),
                            endDate.toString()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        }, true);
    }

    @Override
    public void release(Long roomId, Long bookingId, String authorization) {
        execute(() -> {
            restClient.post()
                    .uri("/api/rooms/{roomId}/release", roomId)
                    .header(HttpHeaders.AUTHORIZATION, serviceAuthorization())
                    .body(new ReleaseRoomRequest(bookingId))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        }, false);
    }

    private <T> T execute(Supplier<T> request, boolean mapsUnavailableRoom) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                if (mapsUnavailableRoom && (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                        || exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT))) {
                    throw new RoomUnavailableException();
                }
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw new HotelServiceUnavailableException(exception);
                }
                lastFailure = exception;
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
            }
            if (attempt < maxAttempts) {
                pause(attempt);
            }
        }
        throw new HotelServiceUnavailableException(lastFailure);
    }

    private String serviceAuthorization() {
        return "Bearer " + jwtTokenService.issueServiceToken();
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(retryBackoff.multipliedBy(attempt).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HotelServiceUnavailableException(exception);
        }
    }
}
