package com.flightbooking.flightbooking.Services;
import com.flightbooking.flightbooking.DTOs.CreateBookingRequest;
import com.flightbooking.flightbooking.Entity.Booking;
import com.flightbooking.flightbooking.Entity.Flight;
import com.flightbooking.flightbooking.Entity.User;
import com.flightbooking.flightbooking.Entity.Airport;
import com.flightbooking.flightbooking.Repo.BookingRepository;
import com.flightbooking.flightbooking.Repo.FlightRepository;
import com.flightbooking.flightbooking.Repo.AirportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final FlightService flightService;
    private final EmailService emailService;

    @Transactional
    public Booking createBooking(User user, Long flightId, Map<String, Object> bookingDetails) {
        try {
            // Validate flight exists and is available
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Check if flight is scheduled and has available seats
            if (flight.getStatus() != Flight.FlightStatus.SCHEDULED && flight.getStatus() != Flight.FlightStatus.FLYING) {
                throw new RuntimeException("Flight is not available for booking. Status: " + flight.getStatus());
            }

            // Get seats to book (default 1)
            Integer seatsToBook = (Integer) bookingDetails.getOrDefault("seatsBooked", 1);

            if (seatsToBook <= 0) {
                throw new RuntimeException("Number of seats must be at least 1");
            }

            if (flight.getAvailableSeats() < seatsToBook) {
                throw new RuntimeException("Not enough seats available. Available: " + flight.getAvailableSeats() + ", Requested: " + seatsToBook);
            }

            // Validate booking is not too far in advance (max 1 month)
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime maxBookingDate = currentTime.plusMonths(1);

            if (flight.getDepartureTime().isAfter(maxBookingDate)) {
                throw new RuntimeException("Cannot book flights more than 1 month in advance");
            }

//            // Validate flight hasn't already departed
//            if (flight.getDepartureTime().isBefore(currentTime)) {
//                throw new RuntimeException("Cannot book flights that have already departed");
//            }

            // Create booking
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setFlight(flight);
            booking.setSeatsBooked(seatsToBook);

            // Calculate total price
            BigDecimal totalPrice = flight.getPrice().multiply(BigDecimal.valueOf(seatsToBook));
            booking.setTotalPrice(totalPrice);

//            // Set passenger details
//            booking.setPassengerName((String) bookingDetails.get("passengerName"));
//            booking.setPassengerEmail((String) bookingDetails.getOrDefault("passengerEmail", user.getEmail()));
//            booking.setPassengerPhone((String) bookingDetails.get("passengerPhone"));

            // Set passenger details - MUST use authenticated user's email
            booking.setPassengerName((String) bookingDetails.get("passengerName"));
            booking.setPassengerEmail(user.getEmail()); // Always use authenticated user's email
            booking.setPassengerPhone((String) bookingDetails.get("passengerPhone"));

// Validate that passenger name is provided
            if (booking.getPassengerName() == null || booking.getPassengerName().trim().isEmpty()) {
                throw new RuntimeException("Passenger name is required");
            }

// Optional: Validate phone number format
            String phone = booking.getPassengerPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                // Basic phone validation (you can make this more strict)
                if (!phone.matches("^\\+?[0-9-]+$")) {
                    throw new RuntimeException("Invalid phone number format");
                }
            }

            // Validate required fields
            if (booking.getPassengerName() == null || booking.getPassengerName().trim().isEmpty()) {
                throw new RuntimeException("Passenger name is required");
            }

            // Update flight available seats
            if (!flightService.updateAvailableSeats(flightId, seatsToBook)) {
                throw new RuntimeException("Failed to update flight seat availability");
            }

            // Save booking
            Booking savedBooking = bookingRepository.save(booking);

            log.info("Booking created successfully: {} for user: {} on flight: {}",
                    savedBooking.getBookingReference(), user.getEmail(), flight.getFlightNumber());

            return savedBooking;

        } catch (Exception e) {
            log.error("Error creating booking for user: {} on flight: {}", user.getEmail(), flightId, e);
            throw new RuntimeException("Booking failed: " + e.getMessage());
        }
    }

    public List<Flight> searchAvailableFlights(String departureCode, String arrivalCode, LocalDateTime departureDate) {
        try {
            // Validate airport codes
            Airport departure = airportRepository.findByCode(departureCode.toUpperCase())
                    .orElseThrow(() -> new RuntimeException("Departure airport not found: " + departureCode));

            Airport arrival = airportRepository.findByCode(arrivalCode.toUpperCase())
                    .orElseThrow(() -> new RuntimeException("Arrival airport not found: " + arrivalCode));

            if (departure.getId().equals(arrival.getId())) {
                throw new RuntimeException("Departure and arrival airports cannot be the same");
            }

//            // Validate date is not in the past
            LocalDateTime currentTime = LocalDateTime.now();
//            if (departureDate.isBefore(currentTime.toLocalDate().atStartOfDay())) {
//                throw new RuntimeException("Cannot search for flights in the past");
//            }

            // Validate date is not more than 1 month in advance
            LocalDateTime maxSearchDate = currentTime.plusMonths(1);
            if (departureDate.isAfter(maxSearchDate)) {
                throw new RuntimeException("Cannot search for flights more than 1 month in advance");
            }

            // Date range for search
            LocalDateTime startOfDay = departureDate.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // Search for flights with SCHEDULED OR FLYING status (since your flights are continuously flying)
       List<Flight> scheduledFlights = flightRepository.findAvailableFlights(
           departure, arrival, startOfDay);

       List<Flight> flyingFlights = flightRepository.findAvailableFlights(
           departure, arrival, startOfDay);

            // Combine both lists
            List<Flight> allFlights = new ArrayList<>();
            allFlights.addAll(scheduledFlights);
            allFlights.addAll(flyingFlights);

            // Remove duplicates and sort by departure time
            return allFlights.stream()
                    .distinct()
                    .sorted((f1, f2) -> f1.getDepartureTime().compareTo(f2.getDepartureTime()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching flights: {} to {} on {}", departureCode, arrivalCode, departureDate, e);
            throw new RuntimeException("Flight search failed: " + e.getMessage());
        }
    }

    public List<Booking> getUserBookings(User user) {
        return bookingRepository.findByUserOrderByBookingDateDesc(user);
    }

    public List<Booking> getUserUpcomingBookings(User user) {
        return bookingRepository.findUpcomingBookings(user, LocalDateTime.now());
    }

    public List<Booking> getUserPastBookings(User user) {
        return bookingRepository.findPastBookings(user, LocalDateTime.now());
    }

    public Optional<Booking> getBookingByReference(String bookingReference) {
        return bookingRepository.findByBookingReference(bookingReference);
    }

    public Optional<Booking> getUserBookingByReference(User user, String bookingReference) {
        Optional<Booking> booking = bookingRepository.findByBookingReference(bookingReference);

        // Ensure the booking belongs to the user
        if (booking.isPresent() && !booking.get().getUser().getId().equals(user.getId())) {
            return Optional.empty();
        }

        return booking;
    }

    // In your BookingService.cancelBooking() method
    @Transactional
    public Booking cancelBooking(User user, String bookingReference) {
        try {
            // Your existing cancellation logic...
            Booking booking = getUserBookingByReference(user, bookingReference)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            // Cancel the booking
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            // ... rest of your cancellation logic

            Booking cancelledBooking = bookingRepository.save(booking);

            // ADD THIS: Send cancellation email
            try {
                emailService.sendBookingCancellationEmail(
                        user.getEmail(),
                        booking.getBookingReference(),
                        booking.getFlightNumber(),
                        booking.getDepartureAirportCode() + " → " + booking.getArrivalAirportCode(),
                        booking.getTotalPrice()
                );
            } catch (Exception e) {
                log.error("Failed to send cancellation email", e);
                // Don't fail the cancellation if email fails
            }

            return cancelledBooking;

        } catch (Exception e) {
            log.error("Error cancelling booking", e);
            throw new RuntimeException("Failed to cancel booking: " + e.getMessage());
        }
    }

    public Map<String, Object> getBookingStatistics(User user) {
        List<Booking> allBookings = getUserBookings(user);
        List<Booking> upcomingBookings = getUserUpcomingBookings(user);
        List<Booking> pastBookings = getUserPastBookings(user);

        long confirmedBookings = allBookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();

        long cancelledBookings = allBookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CANCELLED)
                .count();

        return Map.of(
                "totalBookings", allBookings.size(),
                "upcomingBookings", upcomingBookings.size(),
                "pastBookings", pastBookings.size(),
                "confirmedBookings", confirmedBookings,
                "cancelledBookings", cancelledBookings
        );
    }
    @Transactional
    public Booking createBooking(Long flightId, CreateBookingRequest request, User user) {
        try {
            // Validate flight
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Check if flight is bookable
            if (flight.getStatus() != Flight.FlightStatus.SCHEDULED) {
                throw new RuntimeException("Flight is not available for booking");
            }

            if (flight.getDepartureTime().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Cannot book flights that have already departed");
            }

            // Check seat availability (this check might be redundant after payment, but good for safety)
            if (flight.getAvailableSeats() < request.getSeatsBooked()) {
                throw new RuntimeException("Not enough seats available");
            }

            // Calculate total price
            BigDecimal totalPrice = flight.getPrice().multiply(BigDecimal.valueOf(request.getSeatsBooked()));

            // Create booking
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setFlight(flight);
            booking.setPassengerName(request.getPassengerName());
            booking.setPassengerEmail(request.getPassengerEmail() != null ? request.getPassengerEmail() : user.getEmail());
            booking.setPassengerPhone(request.getPassengerPhone());
            booking.setSeatsBooked(request.getSeatsBooked());
            booking.setTotalPrice(totalPrice);
            booking.setStatus(Booking.BookingStatus.CONFIRMED);

            // Save booking (this will trigger @PrePersist to set other fields)
            booking = bookingRepository.save(booking);

            // Update flight seats (final seat update after payment)
            flight.setAvailableSeats(flight.getAvailableSeats() - request.getSeatsBooked());
            flightRepository.save(flight);

            log.info("Booking created successfully: {} for user: {}", booking.getBookingReference(), user.getEmail());
            return booking;

        } catch (Exception e) {
            log.error("Error creating booking for flight: {} and user: {}", flightId, user.getEmail(), e);
            throw new RuntimeException("Failed to create booking: " + e.getMessage());
        }
    }

    // Method to create booking with the old interface (for backward compatibility)
    @Transactional
    public Booking createBookingAfterPayment(Long flightId, CreateBookingRequest request, User user) {
        try {
            // Validate flight
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Check if flight is bookable (less strict since payment already completed)
            if (flight.getStatus() != Flight.FlightStatus.SCHEDULED && flight.getStatus() != Flight.FlightStatus.FLYING) {
                throw new RuntimeException("Flight is not available for booking. Status: " + flight.getStatus());
            }

            // Calculate total price
            BigDecimal totalPrice = flight.getPrice().multiply(BigDecimal.valueOf(request.getSeatsBooked()));

            // Create booking
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setFlight(flight);
            booking.setPassengerName(request.getPassengerName());
            booking.setPassengerEmail(user.getEmail()); // Always use authenticated user's email
            booking.setPassengerPhone(request.getPassengerPhone());
            booking.setSeatsBooked(request.getSeatsBooked());
            booking.setTotalPrice(totalPrice);
            booking.setStatus(Booking.BookingStatus.CONFIRMED);

            // Validate required fields
            if (booking.getPassengerName() == null || booking.getPassengerName().trim().isEmpty()) {
                throw new RuntimeException("Passenger name is required");
            }

            // Basic phone validation
            String phone = booking.getPassengerPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                if (!phone.matches("^\\+?[0-9-]+$")) {
                    throw new RuntimeException("Invalid phone number format");
                }
            }

            // Save booking (this will trigger @PrePersist to set other fields)
            Booking savedBooking = bookingRepository.save(booking);

            // Note: Seats are already updated during payment verification
            // No need to update seats again here

            log.info("Booking created after payment: {} for user: {} on flight: {}",
                    savedBooking.getBookingReference(), user.getEmail(), flight.getFlightNumber());

            return savedBooking;

        } catch (Exception e) {
            log.error("Error creating booking after payment for user: {} on flight: {}", user.getEmail(), flightId, e);
            throw new RuntimeException("Failed to create booking after payment: " + e.getMessage());
        }
    }

    /**
     * Check flight availability for payment order creation
     */
    public boolean checkFlightAvailabilityForPayment(Long flightId, Integer seatsRequested) {
        try {
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Check if flight is bookable
            if (flight.getStatus() != Flight.FlightStatus.SCHEDULED && flight.getStatus() != Flight.FlightStatus.FLYING) {
                return false;
            }

            // Check seat availability
            if (flight.getAvailableSeats() < seatsRequested) {
                return false;
            }

            // Check booking advance limit (max 1 month)
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime maxBookingDate = currentTime.plusMonths(1);

            if (flight.getDepartureTime().isAfter(maxBookingDate)) {
                return false;
            }

            // Check if flight already departed (commented out to match your existing logic)
            // if (flight.getDepartureTime().isBefore(currentTime)) {
            //     return false;
            // }

            return true;

        } catch (Exception e) {
            log.error("Error checking flight availability for payment: {}", flightId, e);
            return false;
        }
    }

    /**
     * Temporarily reserve seats during payment order creation
     */
    @Transactional
    public void reserveSeatsForPayment(Long flightId, Integer seatsToReserve) {
        try {
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            if (flight.getAvailableSeats() < seatsToReserve) {
                throw new RuntimeException("Not enough seats available for reservation");
            }

            // Temporarily reduce available seats
            flight.setAvailableSeats(flight.getAvailableSeats() - seatsToReserve);
            flightRepository.save(flight);

            log.info("Reserved {} seats for flight {} during payment order creation", seatsToReserve, flight.getFlightNumber());

        } catch (Exception e) {
            log.error("Error reserving seats for payment: {}", flightId, e);
            throw new RuntimeException("Failed to reserve seats: " + e.getMessage());
        }
    }

    /**
     * Release reserved seats if payment fails
     */
    @Transactional
    public void releaseReservedSeats(Long flightId, Integer seatsToRelease) {
        try {
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Release the reserved seats
            flight.setAvailableSeats(flight.getAvailableSeats() + seatsToRelease);
            flightRepository.save(flight);

            log.info("Released {} reserved seats for flight {} due to payment failure", seatsToRelease, flight.getFlightNumber());

        } catch (Exception e) {
            log.error("Error releasing reserved seats: {}", flightId, e);
            throw new RuntimeException("Failed to release reserved seats: " + e.getMessage());
        }
    }

    // Add these methods to your BookingService class
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Booking adminCancelBooking(String bookingReference, String reason) {
        Booking booking = getBookingByReference(bookingReference)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);

        // ADD THESE FIELDS to match your API response:
        booking.setCancellationReason(reason);  // Store the cancellation reason
        booking.setCancelledAt(LocalDateTime.now());  // Set cancellation timestamp

        // Return seats to flight availability
        Flight flight = booking.getFlight();
        flight.setAvailableSeats(flight.getAvailableSeats() + booking.getSeatsBooked());
        flightRepository.save(flight);

        Booking cancelledBooking = bookingRepository.save(booking);

        // Send cancellation email to customer
        try {
            emailService.sendBookingCancellationEmail(
                    booking.getUser().getEmail(),
                    booking.getBookingReference(),
                    booking.getFlightNumber(),
                    booking.getDepartureAirportCode() + " → " + booking.getArrivalAirportCode(),
                    booking.getTotalPrice()
            );
        } catch (Exception e) {
            log.error("Failed to send admin cancellation email", e);
            // Don't fail the cancellation if email fails
        }

        log.info("Admin cancelled booking: {} for reason: {}", bookingReference, reason);
        return cancelledBooking;
    }

    public Map<String, Object> getBookingAnalytics() {
        List<Booking> allBookings = getAllBookings();

        Map<String, Long> statusCount = allBookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStatus().toString(),
                        Collectors.counting()
                ));

        Map<String, Long> airlineCount = allBookings.stream()
                .collect(Collectors.groupingBy(
                        Booking::getAirline,
                        Collectors.counting()
                ));

        return Map.of(
                "bookingsByStatus", statusCount,
                "bookingsByAirline", airlineCount,
                "totalBookings", allBookings.size()
        );
    }

    public Map<String, Object> getPopularRoutesAnalytics() {
        List<Booking> allBookings = getAllBookings();

        Map<String, Long> routeCount = allBookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDepartureAirportCode() + " → " + b.getArrivalAirportCode(),
                        Collectors.counting()
                ));

        return Map.of(
                "popularRoutes", routeCount,
                "totalRoutes", routeCount.size()
        );
    }
}