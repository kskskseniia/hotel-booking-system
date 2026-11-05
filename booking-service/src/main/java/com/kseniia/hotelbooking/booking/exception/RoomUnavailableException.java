package com.kseniia.hotelbooking.booking.exception;

public class RoomUnavailableException extends RuntimeException {

    public RoomUnavailableException() {
        super("No room is available for the requested dates");
    }
}
