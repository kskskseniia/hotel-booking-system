package com.kseniia.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Lob
    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked;

    protected Room() {
    }

    public Room(Hotel hotel, String number) {
        this.hotel = hotel;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public String getNumber() {
        return number;
    }

    public boolean isAvailable() {
        return available;
    }

    public long getTimesBooked() {
        return timesBooked;
    }

    public void incrementTimesBooked() {
        timesBooked++;
    }

    public void decrementTimesBooked() {
        if (timesBooked > 0) {
            timesBooked--;
        }
    }
}
