package com.kseniia.hotelbooking.booking.client;

record ConfirmAvailabilityRequest(
        Long bookingId,
        String startDate,
        String endDate
) {
}
