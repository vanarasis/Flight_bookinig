package com.flightbooking.flightbooking.Services;
import com.flightbooking.flightbooking.Entity.Airport;
import com.flightbooking.flightbooking.Repo.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AirportService {
    private final AirportRepository airportRepository;

    public Airport createAirport(Airport airport) {
        if (airportRepository.existsByCode(airport.getCode())) {
            throw new RuntimeException("Airport with code " + airport.getCode() + " already exists");
        }
        airport.setCode(airport.getCode().toUpperCase());
        return airportRepository.save(airport);
    }

    // In AirportService
    public Optional<Airport> getAirportByCode(String code) {
        return airportRepository.findByCode(code.toUpperCase());
    }

    public List<Airport> getAllAirports() {
        return airportRepository.findAllOrderByName();
    }

    public Optional<Airport> getAirportById(Long id) {
        return airportRepository.findById(id);
    }

    public Airport updateAirport(Long id, Airport airportDetails) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Airport not found with id: " + id));

        airport.setName(airportDetails.getName());
        airport.setCity(airportDetails.getCity());
        airport.setCountry(airportDetails.getCountry());

        return airportRepository.save(airport);
    }

    public void deleteAirport(Long id) {
        if (!airportRepository.existsById(id)) {
            throw new RuntimeException("Airport not found with id: " + id);
        }
        airportRepository.deleteById(id);
    }
}
