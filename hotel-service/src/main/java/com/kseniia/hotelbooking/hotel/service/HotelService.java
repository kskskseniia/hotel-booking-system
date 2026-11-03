package com.kseniia.hotelbooking.hotel.service;

import com.kseniia.hotelbooking.hotel.dto.CreateHotelRequest;
import com.kseniia.hotelbooking.hotel.dto.HotelResponse;
import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Transactional
    public HotelResponse createHotel(CreateHotelRequest request) {
        Hotel hotel = hotelRepository.save(new Hotel(request.name(), request.address()));
        return toResponse(hotel);
    }

    @Transactional(readOnly = true)
    public List<HotelResponse> getHotels() {
        return hotelRepository.findAll().stream()
                .map(HotelService::toResponse)
                .toList();
    }

    private static HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
