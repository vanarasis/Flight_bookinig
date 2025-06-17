package com.flightbooking.flightbooking.Util;
import com.flightbooking.flightbooking.Entity.Flight;
import com.flightbooking.flightbooking.Repo.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightStatusScheduler {
    private final FlightRepository flightRepository;

    // Run every 2 minutes to check flight status updates
    @Scheduled(fixedRate = 120000) // 2 minutes in milliseconds
    public void updateFlightStatuses() {
        try {
            // Get current time in IST
            ZonedDateTime istNow = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            LocalDateTime currentTime = istNow.toLocalDateTime();
            log.info("Checking flight statuses at IST: {}", currentTime);

            // Find all active flights (not cancelled)
            List<Flight> activeFlights = flightRepository.findByStatusIn(
                    Arrays.asList(Flight.FlightStatus.SCHEDULED, Flight.FlightStatus.FLYING, Flight.FlightStatus.COMPLETED)
            );

            int updatedCount = 0;

            for (Flight flight : activeFlights) {
                boolean statusChanged = false;
                boolean flightUpdated = false;

                // Reset cycle count if 24 hours have passed
                if (flight.shouldResetCycleCount()) {
                    flight.resetCycleCount();
                    log.info("Flight {} cycle count reset to 0 after 24 hours", flight.getFlightNumber());
                    flightUpdated = true;
                }

                // Status transitions based on current flight state
                switch (flight.getStatus()) {
                    case SCHEDULED:
                        // Check if flight should start flying
                        if (currentTime.isAfter(flight.getDepartureTime()) || currentTime.isEqual(flight.getDepartureTime())) {
                            flight.setStatus(Flight.FlightStatus.FLYING);
                            statusChanged = true;
                            log.info("Flight {} is now FLYING. Departed from {} to {}",
                                    flight.getFlightNumber(),
                                    flight.getDepartureAirport().getCode(),
                                    flight.getArrivalAirport().getCode());
                        }
                        break;

                    case FLYING:
                        // Check if flight has reached destination
                        if (currentTime.isAfter(flight.getArrivalTime()) || currentTime.isEqual(flight.getArrivalTime())) {
                            flight.setStatus(Flight.FlightStatus.COMPLETED);
                            statusChanged = true;
                            log.info("Flight {} has COMPLETED journey. Arrived at {} from {}",
                                    flight.getFlightNumber(),
                                    flight.getArrivalAirport().getCode(),
                                    flight.getDepartureAirport().getCode());
                        }
                        break;

                    case COMPLETED:
                        // Check if ground time (1 hour) has passed, then prepare for next flight
                        LocalDateTime nextFlightTime = flight.getArrivalTime().plusHours(flight.getGroundTimeHours().longValue());
                        if (currentTime.isAfter(nextFlightTime) || currentTime.isEqual(nextFlightTime)) {
                            // Increment cycle count (one leg of journey completed)
                            flight.incrementCycleCount();

                            // Reverse the route
                            flight.reverseRoute();

                            // Calculate new flight times - ADD MORE TIME TO CREATE FUTURE FLIGHTS
                            LocalDateTime newDepartureTime = flight.getArrivalTime().plusHours(flight.getGroundTimeHours().longValue());

                            // CREATE MULTIPLE FUTURE FLIGHTS (GENERATE 30 DAYS AHEAD)
                            // Add extra hours to spread flights across future dates
                            long extraHours = (flight.getCycleCount() * 6); // 6 hours per cycle to spread across days
                            newDepartureTime = newDepartureTime.plusHours(extraHours);

                            flight.setDepartureTime(newDepartureTime);
                            flight.setArrivalTime(newDepartureTime.plusHours(flight.getFlightDurationHours().longValue()));
                            flight.setNextDepartureTime(newDepartureTime);

                            // Set status back to SCHEDULED for next flight
                            flight.setStatus(Flight.FlightStatus.SCHEDULED);

                            // Reset available seats for new flight
                            flight.setAvailableSeats(flight.getTotalSeats());

                            statusChanged = true;
                            log.info("Flight {} prepared for next journey. Route: {} to {}. Cycle count: {}. Departure: {}",
                                    flight.getFlightNumber(),
                                    flight.getDepartureAirport().getCode(),
                                    flight.getArrivalAirport().getCode(),
                                    flight.getCycleCount(),
                                    flight.getDepartureTime());
                        }
                        break;
                }

                if (statusChanged || flightUpdated) {
                    flightRepository.save(flight);
                    updatedCount++;
                }
            }

            if (updatedCount > 0) {
                log.info("Updated {} flights", updatedCount);
            } else {
                log.debug("No flight updates required at this time");
            }

        } catch (Exception e) {
            log.error("Error updating flight statuses: {}", e.getMessage(), e);
        }
    }

    // Manual method for testing - can be called via REST endpoint
    public String updateFlightStatusesManually() {
        updateFlightStatuses();
        return "Flight status update triggered manually at " + LocalDateTime.now();
    }

    // Method to get flight cycle statistics
    public String getFlightCycleStats() {
        try {
            List<Flight> allFlights = flightRepository.findAll();
            StringBuilder stats = new StringBuilder();
            stats.append("Flight Cycle Statistics:\n");

            for (Flight flight : allFlights) {
                stats.append(String.format("Flight %s: %d cycles completed. Last reset: %s. Current route: %s → %s\n",
                        flight.getFlightNumber(),
                        flight.getCycleCount(),
                        flight.getLastCycleReset(),
                        flight.getDepartureAirport().getCode(),
                        flight.getArrivalAirport().getCode()));
            }

            return stats.toString();
        } catch (Exception e) {
            return "Error retrieving flight cycle statistics: " + e.getMessage();
        }
    }

    
}
