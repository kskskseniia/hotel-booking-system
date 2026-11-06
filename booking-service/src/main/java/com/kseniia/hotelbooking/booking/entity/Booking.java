package com.kseniia.hotelbooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookings_user_request",
                columnNames = {"user_id", "request_id"}
        )
)
public class Booking {

    private static final long UNASSIGNED_ROOM_ID = Long.MIN_VALUE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "request_auto_select")
    private Boolean requestAutoSelect;

    @Column(name = "request_room_id")
    private Long requestRoomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", length = 32)
    private BookingRequestStatus requestStatus;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Booking() {
    }

    public Booking(
            User user,
            String requestId,
            Boolean requestAutoSelect,
            Long requestRoomId,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            BookingStatus status,
            LocalDateTime createdAt
    ) {
        this.user = user;
        this.requestId = requestId;
        this.requestAutoSelect = requestAutoSelect;
        this.requestRoomId = requestRoomId;
        this.requestStatus = BookingRequestStatus.PROCESSING;
        this.roomId = roomId == null ? UNASSIGNED_ROOM_ID : roomId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Booking(
            User user,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            BookingStatus status,
            LocalDateTime createdAt
    ) {
        this(user, null, null, null, roomId, startDate, endDate, status, createdAt);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean matchesRequest(
            boolean autoSelect,
            Long requestedRoomId,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate
    ) {
        return Objects.equals(requestAutoSelect, autoSelect)
                && Objects.equals(requestRoomId, requestedRoomId)
                && startDate.equals(requestedStartDate)
                && endDate.equals(requestedEndDate);
    }

    public Long getRoomId() {
        return roomId != null && roomId == UNASSIGNED_ROOM_ID ? null : roomId;
    }

    public BookingRequestStatus getRequestStatus() {
        return requestStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void confirm() {
        requirePending();
        status = BookingStatus.CONFIRMED;
        requestStatus = BookingRequestStatus.SUCCEEDED;
    }

    public void assignRoom(Long roomId) {
        requirePending();
        this.roomId = Objects.requireNonNull(roomId, "roomId");
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
    }

    public void failRequest(BookingRequestStatus requestStatus, boolean cancelBooking) {
        this.requestStatus = requestStatus;
        if (cancelBooking) {
            status = BookingStatus.CANCELLED;
        }
    }

    private void requirePending() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is no longer pending");
        }
    }
}
