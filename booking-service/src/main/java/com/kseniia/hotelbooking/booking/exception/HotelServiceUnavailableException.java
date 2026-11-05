package com.kseniia.hotelbooking.booking.exception;

public class HotelServiceUnavailableException extends RuntimeException {

    public HotelServiceUnavailableException(Throwable cause) {
        super("Hotel service is unavailable", cause);
    }
}
