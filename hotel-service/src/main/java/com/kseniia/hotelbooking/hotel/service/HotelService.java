package com.kseniia.hotelbooking.hotel.service;

import com.kseniia.hotelbooking.hotel.dto.CreateHotelRequest;
import com.kseniia.hotelbooking.hotel.dto.HotelResponse;
import com.kseniia.hotelbooking.hotel.dto.UpdateHotelRequest;
import com.kseniia.hotelbooking.hotel.entity.Hotel;
import com.kseniia.hotelbooking.hotel.repository.HotelRepository;
import com.kseniia.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    public HotelService(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
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

    @Transactional
    public HotelResponse updateHotel(Long hotelId, UpdateHotelRequest request) {
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hotel not found"));
        hotel.update(request.name(), request.address());
        return toResponse(hotel);
    }

    @Transactional
    public void deleteHotel(Long hotelId) {
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hotel not found"));
        if (roomRepository.existsByHotelId(hotelId)) {
            throw new ResponseStatusException(CONFLICT, "Hotel still has rooms");
        }
        hotelRepository.delete(hotel);
    }

    private static HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
