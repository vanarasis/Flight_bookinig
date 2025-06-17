package com.flightbooking.flightbooking.Services;
import com.flightbooking.flightbooking.Entity.Flight;
import com.flightbooking.flightbooking.Entity.Airport;
import com.flightbooking.flightbooking.Repo.AirportRepository;
import com.flightbooking.flightbooking.Repo.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightService {
    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;

    public Flight createFlight(Flight flight) {
        if (flightRepository.existsByFlightNumber(flight.getFlightNumber())) {
            throw new RuntimeException("Flight with number " + flight.getFlightNumber() + " already exists");
        }

        // Validate airports exist
        if (flight.getDepartureAirport() == null || flight.getArrivalAirport() == null) {
            throw new RuntimeException("Departure and arrival airports are required");
        }

        // Fetch complete airport details from database
        Airport departureAirport = airportRepository.findById(flight.getDepartureAirport().getId())
                .orElseThrow(() -> new RuntimeException("Departure airport not found with id: " + flight.getDepartureAirport().getId()));

        Airport arrivalAirport = airportRepository.findById(flight.getArrivalAirport().getId())
                .orElseThrow(() -> new RuntimeException("Arrival airport not found with id: " + flight.getArrivalAirport().getId()));

        if (departureAirport.getId().equals(arrivalAirport.getId())) {
            throw new RuntimeException("Departure and arrival airports cannot be the same");
        }

        // Set the complete airport objects
        flight.setDepartureAirport(departureAirport);
        flight.setArrivalAirport(arrivalAirport);

        // Set original airports for continuous flight tracking
        flight.setOriginalDepartureAirport(departureAirport);
        flight.setOriginalArrivalAirport(arrivalAirport);

        // Initialize cycle tracking
        flight.setCycleCount(0);
        flight.setLastCycleReset(LocalDateTime.now());
        flight.setIsRouteReversed(false);

        return flightRepository.save(flight);
    }

    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    public Optional<Flight> getFlightById(Long id) {
        return flightRepository.findById(id);
    }

    public List<Flight> searchFlights(String departureCode, String arrivalCode, LocalDateTime departureDate) {
        System.out.println("=== FLIGHT SEARCH DEBUG ===");
        System.out.println("Input: " + departureCode + " -> " + arrivalCode + " on " + departureDate);

        Airport departure = airportRepository.findByCode(departureCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Departure airport not found: " + departureCode));
        System.out.println("Departure airport: " + departure.getCode() + " (ID: " + departure.getId() + ")");

        Airport arrival = airportRepository.findByCode(arrivalCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Arrival airport not found: " + arrivalCode));
        System.out.println("Arrival airport: " + arrival.getCode() + " (ID: " + arrival.getId() + ")");

        LocalDateTime startOfDay = departureDate.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        System.out.println("Searching between: " + startOfDay + " and " + endOfDay);

        // Use the updated method that accepts multiple statuses
      List<Flight> flights = flightRepository.findAvailableFlights(departure, arrival, startOfDay);

        System.out.println("Found flights: " + flights.size());
        return flights;
    }

    public Flight updateFlight(Long id, Flight flightDetails) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Flight not found with id: " + id));

        flight.setFlightNumber(flightDetails.getFlightNumber());

        // Fetch complete airport details if airport IDs are provided
        if (flightDetails.getDepartureAirport() != null && flightDetails.getDepartureAirport().getId() != null) {
            Airport departureAirport = airportRepository.findById(flightDetails.getDepartureAirport().getId())
                    .orElseThrow(() -> new RuntimeException("Departure airport not found with id: " + flightDetails.getDepartureAirport().getId()));
            flight.setDepartureAirport(departureAirport);
        }

        if (flightDetails.getArrivalAirport() != null && flightDetails.getArrivalAirport().getId() != null) {
            Airport arrivalAirport = airportRepository.findById(flightDetails.getArrivalAirport().getId())
                    .orElseThrow(() -> new RuntimeException("Arrival airport not found with id: " + flightDetails.getArrivalAirport().getId()));
            flight.setArrivalAirport(arrivalAirport);
        }

        flight.setDepartureTime(flightDetails.getDepartureTime());
        flight.setArrivalTime(flightDetails.getArrivalTime());
        flight.setAirline(flightDetails.getAirline());
        flight.setPrice(flightDetails.getPrice());
        flight.setTotalSeats(flightDetails.getTotalSeats());
        flight.setAvailableSeats(flightDetails.getAvailableSeats());
        flight.setStatus(flightDetails.getStatus());

        // Update flight duration and ground time if provided
        if (flightDetails.getFlightDurationHours() != null) {
            flight.setFlightDurationHours(flightDetails.getFlightDurationHours());
        }
        if (flightDetails.getGroundTimeHours() != null) {
            flight.setGroundTimeHours(flightDetails.getGroundTimeHours());
        }

        return flightRepository.save(flight);
    }

    public void deleteFlight(Long id) {
        if (!flightRepository.existsById(id)) {
            throw new RuntimeException("Flight not found with id: " + id);
        }
        flightRepository.deleteById(id);
    }

    public boolean updateAvailableSeats(Long flightId, int seatsToBook) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        if (flight.getAvailableSeats() >= seatsToBook) {
            flight.setAvailableSeats(flight.getAvailableSeats() - seatsToBook);
            flightRepository.save(flight);
            return true;
        }
        return false;
    }

    // New method to get flight cycle information
    public String getFlightCycleInfo(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        return String.format("Flight %s has completed %d cycles. Current route: %s → %s. Route reversed: %s",
                flight.getFlightNumber(),
                flight.getCycleCount(),
                flight.getDepartureAirport().getCode(),
                flight.getArrivalAirport().getCode(),
                flight.getIsRouteReversed() ? "Yes" : "No");
    }

    // Method to manually reset cycle count for a specific flight
    public String resetFlightCycleCount(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        flight.resetCycleCount();
        flightRepository.save(flight);

        return String.format("Cycle count reset for flight %s", flight.getFlightNumber());
    }
}
