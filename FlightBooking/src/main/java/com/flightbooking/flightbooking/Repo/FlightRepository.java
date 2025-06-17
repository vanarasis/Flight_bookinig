package com.flightbooking.flightbooking.Repo;
import com.flightbooking.flightbooking.Entity.Flight;
import com.flightbooking.flightbooking.Entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long>{
    boolean existsByFlightNumber(String flightNumber);

    @Query("SELECT f FROM Flight f WHERE f.departureAirport = :departure AND f.arrivalAirport = :arrival AND DATE(f.departureTime) = DATE(:departureDate) AND f.status IN ('SCHEDULED', 'FLYING', 'COMPLETED') AND f.availableSeats > 0 ORDER BY f.departureTime ASC")
    List<Flight> findAvailableFlights(@Param("departure") Airport departure,
                                      @Param("arrival") Airport arrival,
                                      @Param("departureDate") LocalDateTime departureDate);
    // Alternative simpler query for debugging
    @Query("SELECT f FROM Flight f WHERE " +
            "f.departureAirport.code = :departureCode AND " +
            "f.arrivalAirport.code = :arrivalCode AND " +
            "f.status = 'SCHEDULED' AND " +
            "f.availableSeats > 0 " +
            "ORDER BY f.departureTime ASC")
    List<Flight> findAvailableFlightsByCode(@Param("departureCode") String departureCode,
                                            @Param("arrivalCode") String arrivalCode);

    // Core method used by scheduler
    List<Flight> findByStatusIn(List<Flight.FlightStatus> statuses);

    // Get all flights for a specific route (for debugging)
    @Query("SELECT f FROM Flight f WHERE " +
            "f.departureAirport.code = :departureCode AND " +
            "f.arrivalAirport.code = :arrivalCode " +
            "ORDER BY f.departureTime ASC")
    List<Flight> findAllFlightsByRoute(@Param("departureCode") String departureCode,
                                       @Param("arrivalCode") String arrivalCode);



}