package com.kseniia.hotelbooking.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kseniia.hotelbooking.booking.client.HotelClient;
import com.kseniia.hotelbooking.booking.client.RoomCandidate;
import com.kseniia.hotelbooking.booking.entity.Booking;
import com.kseniia.hotelbooking.booking.entity.BookingStatus;
import com.kseniia.hotelbooking.booking.entity.User;
import com.kseniia.hotelbooking.booking.entity.UserRole;
import com.kseniia.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.kseniia.hotelbooking.booking.exception.RoomUnavailableException;
import com.kseniia.hotelbooking.booking.repository.BookingRepository;
import com.kseniia.hotelbooking.booking.repository.UserRepository;
import com.kseniia.hotelbooking.booking.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
@AutoConfigureMockMvc
class BookingControllerTest {

    private static final LocalDate START_DATE = LocalDate.of(2099, 7, 10);
    private static final LocalDate END_DATE = LocalDate.of(2099, 7, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private HotelClient hotelClient;

    private User user;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        reset(hotelClient);
        user = userRepository.saveAndFlush(new User("booking-user", "encoded-password", UserRole.USER));
    }

    @Test
    void createsConfirmedBookingWithSelectedRoom() throws Exception {
        String authorization = bearer(user);

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false, 42L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roomId").value(42))
                .andExpect(jsonPath("$.startDate").value("2099-07-10"))
                .andExpect(jsonPath("$.endDate").value("2099-07-12"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Booking booking = bookingRepository.findAll().getFirst();
        assertThat(booking.getUser().getId()).isEqualTo(user.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(hotelClient).confirmAvailability(
                42L,
                booking.getId(),
                START_DATE,
                END_DATE,
                authorization
        );
        verify(hotelClient, never()).release(any(), any(), any());
    }

    @Test
    void cancelsBookingAndReleasesRoomWhenHotelRejectsDates() throws Exception {
        String authorization = bearer(user);
        doThrow(new RoomUnavailableException())
                .when(hotelClient)
                .confirmAvailability(eq(42L), any(), eq(START_DATE), eq(END_DATE), any());

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false, 42L)))
                .andExpect(status().isConflict());

        Booking booking = bookingRepository.findAll().getFirst();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(hotelClient).release(42L, booking.getId(), authorization);
    }

    @Test
    void keepsBookingPendingWhenCompensatingReleaseFails() throws Exception {
        String authorization = bearer(user);
        HotelServiceUnavailableException failure = new HotelServiceUnavailableException(new RuntimeException("down"));
        doThrow(failure).when(hotelClient)
                .confirmAvailability(eq(42L), any(), eq(START_DATE), eq(END_DATE), any());
        doThrow(failure).when(hotelClient).release(eq(42L), any(), any());

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false, 42L)))
                .andExpect(status().isServiceUnavailable());

        Booking booking = bookingRepository.findAll().getFirst();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void autoSelectsRecommendedRoomAndIgnoresRequestRoomId() throws Exception {
        String authorization = bearer(user);
        when(hotelClient.getRecommendedRooms(authorization))
                .thenReturn(List.of(new RoomCandidate(7L), new RoomCandidate(8L)));

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(true, 999L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(7))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Booking booking = bookingRepository.findAll().getFirst();
        verify(hotelClient).confirmAvailability(
                7L,
                booking.getId(),
                START_DATE,
                END_DATE,
                authorization
        );
        verify(hotelClient, never()).confirmAvailability(eq(999L), any(), any(), any(), any());
    }

    @Test
    void autoSelectTriesNextRecommendedRoomAfterConflict() throws Exception {
        String authorization = bearer(user);
        when(hotelClient.getRecommendedRooms(authorization))
                .thenReturn(List.of(new RoomCandidate(7L), new RoomCandidate(8L)));
        doThrow(new RoomUnavailableException())
                .when(hotelClient)
                .confirmAvailability(eq(7L), any(), eq(START_DATE), eq(END_DATE), any());

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(8))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Booking booking = bookingRepository.findAll().getFirst();
        assertThat(booking.getRoomId()).isEqualTo(8L);
        verify(hotelClient).confirmAvailability(7L, booking.getId(), START_DATE, END_DATE, authorization);
        verify(hotelClient).confirmAvailability(8L, booking.getId(), START_DATE, END_DATE, authorization);
        verify(hotelClient, never()).release(eq(7L), any(), any());
    }

    @Test
    void rejectsManualSelectionWithoutRoomId() throws Exception {
        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false, null)))
                .andExpect(status().isBadRequest());

        assertThat(bookingRepository.count()).isZero();
        verifyNoInteractions(hotelClient);
    }

    @Test
    void adminCannotCreateBooking() throws Exception {
        User admin = userRepository.saveAndFlush(new User("booking-admin", "encoded-password", UserRole.ADMIN));

        mockMvc.perform(post("/booking")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false, 42L)))
                .andExpect(status().isForbidden());

        assertThat(bookingRepository.count()).isZero();
        verifyNoInteractions(hotelClient);
    }

    @Test
    void returnsOnlyAuthenticatedUsersBookingHistoryNewestFirst() throws Exception {
        User anotherUser = userRepository.saveAndFlush(
                new User("another-booking-user", "encoded-password", UserRole.USER)
        );
        bookingRepository.saveAllAndFlush(List.of(
                new Booking(
                        user,
                        41L,
                        LocalDate.of(2099, 1, 10),
                        LocalDate.of(2099, 1, 12),
                        BookingStatus.CONFIRMED,
                        LocalDateTime.of(2098, 12, 1, 10, 0)
                ),
                new Booking(
                        user,
                        42L,
                        LocalDate.of(2099, 2, 10),
                        LocalDate.of(2099, 2, 12),
                        BookingStatus.CANCELLED,
                        LocalDateTime.of(2098, 12, 2, 10, 0)
                ),
                new Booking(
                        anotherUser,
                        99L,
                        LocalDate.of(2099, 3, 10),
                        LocalDate.of(2099, 3, 12),
                        BookingStatus.CONFIRMED,
                        LocalDateTime.of(2098, 12, 3, 10, 0)
                )
        ));

        mockMvc.perform(get("/bookings")
                        .header(AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].roomId").value(42))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].roomId").value(41))
                .andExpect(jsonPath("$[1].status").value("CONFIRMED"));
    }

    @Test
    void userCannotViewAllBookingsForRoom() throws Exception {
        mockMvc.perform(get("/bookings/rooms/42")
                        .header(AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminViewsBookingsForSelectedRoomWithUserIds() throws Exception {
        User admin = userRepository.saveAndFlush(new User("room-admin", "encoded-password", UserRole.ADMIN));
        User anotherUser = userRepository.saveAndFlush(
                new User("room-booking-user", "encoded-password", UserRole.USER)
        );
        bookingRepository.saveAllAndFlush(List.of(
                new Booking(
                        user,
                        42L,
                        LocalDate.of(2099, 2, 10),
                        LocalDate.of(2099, 2, 12),
                        BookingStatus.CONFIRMED,
                        LocalDateTime.of(2098, 12, 2, 10, 0)
                ),
                new Booking(
                        anotherUser,
                        42L,
                        LocalDate.of(2099, 1, 10),
                        LocalDate.of(2099, 1, 12),
                        BookingStatus.CANCELLED,
                        LocalDateTime.of(2098, 12, 1, 10, 0)
                ),
                new Booking(
                        anotherUser,
                        99L,
                        LocalDate.of(2099, 3, 10),
                        LocalDate.of(2099, 3, 12),
                        BookingStatus.CONFIRMED,
                        LocalDateTime.of(2098, 12, 3, 10, 0)
                )
        ));

        mockMvc.perform(get("/bookings/rooms/42")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(anotherUser.getId()))
                .andExpect(jsonPath("$[0].roomId").value(42))
                .andExpect(jsonPath("$[0].startDate").value("2099-01-10"))
                .andExpect(jsonPath("$[1].userId").value(user.getId()))
                .andExpect(jsonPath("$[1].roomId").value(42))
                .andExpect(jsonPath("$[1].startDate").value("2099-02-10"));
    }

    private String request(boolean autoSelect, Long roomId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("autoSelect", autoSelect);
        if (roomId != null) {
            request.put("roomId", roomId);
        }
        request.put("startDate", START_DATE);
        request.put("endDate", END_DATE);
        return objectMapper.writeValueAsString(request);
    }

    private String bearer(User tokenOwner) {
        return "Bearer " + jwtTokenService.issueToken(tokenOwner).token();
    }
}
