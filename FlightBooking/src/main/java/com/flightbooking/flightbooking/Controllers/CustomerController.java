package com.flightbooking.flightbooking.Controllers;
import com.flightbooking.flightbooking.Entity.*;
import com.flightbooking.flightbooking.Services.*;
import com.flightbooking.flightbooking.Util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.flightbooking.flightbooking.DTOs.*;
import com.flightbooking.flightbooking.Services.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CustomerController {
    private final PaymentService paymentService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AirportService airportService;
    private final FlightService flightService;
    private final BookingService bookingService;
    private final EmailService emailService;

    // =================
    // DASHBOARD & AUTH ENDPOINTS
    // =================

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getDashboard(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            Map<String, Object> bookingStats = bookingService.getBookingStatistics(currentUser);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Welcome to Customer Dashboard",
                    "user", Map.of(
                            "email", currentUser.getEmail(),
                            "role", currentUser.getRole(),
                            "lastLogin", currentUser.getLastLogin()
                    ),
                    "bookingStatistics", bookingStats
            ));
        } catch (Exception e) {
            log.error("Customer dashboard error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", currentUser.getId(),
                            "email", currentUser.getEmail(),
                            "role", currentUser.getRole(),
                            "isVerified", currentUser.getIsVerified(),
                            "createdAt", currentUser.getCreatedAt(),
                            "lastLogin", currentUser.getLastLogin()
                    )
            ));
        } catch (Exception e) {
            log.error("Get profile error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            String sessionId = (String) request.getAttribute("sessionId");
            String result = authService.logout(sessionId);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Customer logout error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // FLIGHT SEARCH ENDPOINTS
    // =================

    @GetMapping("/airports")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getAllAirports() {
        try {
            List<Airport> airports = airportService.getAllAirports();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airports retrieved successfully",
                    "data", airports,
                    "availableCodes", List.of("DEL", "BOM", "MAA", "CCU", "HYD", "COH")
            ));
        } catch (Exception e) {
            log.error("Get airports error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/search")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> searchFlights(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String departureDate) {
        try {
            // Parse date - support both YYYY-MM-DD and YYYY-MM-DDTHH:MM:SS formats
            LocalDateTime date;
            try {
                if (departureDate.contains("T")) {
                    date = LocalDateTime.parse(departureDate);
                } else {
                    date = LocalDateTime.parse(departureDate + "T00:00:00");
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid date format. Use YYYY-MM-DD"));
            }

            List<Flight> flights = bookingService.searchAvailableFlights(departure, arrival, date);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flights retrieved successfully",
                    "searchCriteria", Map.of(
                            "departure", departure.toUpperCase(),
                            "arrival", arrival.toUpperCase(),
                            "date", departureDate
                    ),
                    "totalFlights", flights.size(),
                    "data", flights
            ));
        } catch (Exception e) {
            log.error("Search flights error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // BOOKING ENDPOINTS
    // =================

    // Replace your existing bookFlight method with this updated version
    @PostMapping("/flights/{flightId}/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> bookFlight(
            @PathVariable Long flightId,
            @RequestBody Map<String, Object> bookingRequest,
            HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");

            // Extract passenger details
            String passengerName = (String) bookingRequest.get("passengerName");
            String passengerPhone = (String) bookingRequest.get("passengerPhone");
            Integer seatsBooked = (Integer) bookingRequest.get("seatsBooked");

            // Validate input
            if (passengerName == null || passengerName.trim().isEmpty()) {
                throw new RuntimeException("Passenger name is required");
            }
            if (passengerPhone == null || passengerPhone.trim().isEmpty()) {
                throw new RuntimeException("Passenger phone is required");
            }
            if (seatsBooked == null || seatsBooked <= 0) {
                throw new RuntimeException("Number of seats must be greater than 0");
            }

            // Create payment order instead of direct booking
            PaymentOrderRequest paymentRequest = new PaymentOrderRequest();
            paymentRequest.setFlightId(flightId);
            paymentRequest.setPassengerName(passengerName);
            paymentRequest.setPassengerPhone(passengerPhone);
            paymentRequest.setSeatsBooked(seatsBooked);

            PaymentOrderResponse paymentResponse = paymentService.createPaymentOrder(paymentRequest, currentUser.getEmail());

          return ResponseEntity.ok(Map.of(
                  "success", true,
                  "message", "Payment order created successfully. Please complete the payment.",
                  "data", Map.ofEntries(
                          Map.entry("orderId", paymentResponse.getOrderId()),
                          Map.entry("razorpayOrderId", paymentResponse.getRazorpayOrderId()),
                          Map.entry("amount", paymentResponse.getAmount()),
                          Map.entry("currency", paymentResponse.getCurrency()),
                          Map.entry("flightNumber", paymentResponse.getFlightNumber()),
                          Map.entry("route", paymentResponse.getRoute()),
                          Map.entry("departureTime", paymentResponse.getDepartureTime()),
                          Map.entry("arrivalTime", paymentResponse.getArrivalTime()),
                          Map.entry("seatsBooked", paymentResponse.getSeatsBooked()),
                          Map.entry("passengerName", paymentResponse.getPassengerName()),
                          Map.entry("razorpayKeyId", paymentResponse.getRazorpayKeyId())
                  )
          ));
        } catch (Exception e) {
            log.error("Booking flight error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }


    // Add this new endpoint for direct booking (for testing without payment)
    @PostMapping("/flights/{flightId}/book-direct")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> bookFlightDirect(
            @PathVariable Long flightId,
            @RequestBody Map<String, Object> bookingRequest,
            HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");

            Booking booking = bookingService.createBooking(currentUser, flightId, bookingRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight booked successfully (direct booking)",
                    "data", Map.of(
                            "bookingReference", booking.getBookingReference(),
                            "totalPrice", booking.getTotalPrice(),
                            "seatsBooked", booking.getSeatsBooked(),
                            "flightNumber", booking.getFlightNumber(),
                            "departureTime", booking.getDepartureTime(),
                            "arrivalTime", booking.getArrivalTime(),
                            "route", booking.getDepartureAirportCode() + " → " + booking.getArrivalAirportCode(),
                            "passengerName", booking.getPassengerName(),
                            "status", booking.getStatus()
                    )
            ));
        } catch (Exception e) {
            log.error("Direct booking flight error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getAllBookings(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            List<Booking> bookings = bookingService.getUserBookings(currentUser);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Bookings retrieved successfully",
                    "totalBookings", bookings.size(),
                    "data", bookings
            ));
        } catch (Exception e) {
            log.error("Get bookings error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/upcoming")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getUpcomingBookings(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            List<Booking> bookings = bookingService.getUserUpcomingBookings(currentUser);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Upcoming bookings retrieved successfully",
                    "totalBookings", bookings.size(),
                    "data", bookings
            ));
        } catch (Exception e) {
            log.error("Get upcoming bookings error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/past")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getPastBookings(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            List<Booking> bookings = bookingService.getUserPastBookings(currentUser);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Past bookings retrieved successfully",
                    "totalBookings", bookings.size(),
                    "data", bookings
            ));
        } catch (Exception e) {
            log.error("Get past bookings error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/{bookingReference}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getBookingDetails(
            @PathVariable String bookingReference,
            HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            Optional<Booking> booking = bookingService.getUserBookingByReference(currentUser, bookingReference);

            if (booking.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Booking not found"));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking details retrieved successfully",
                    "data", booking.get()
            ));
        } catch (Exception e) {
            log.error("Get booking details error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/bookings/{bookingReference}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> cancelBooking(
            @PathVariable String bookingReference,
            HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            Booking cancelledBooking = bookingService.cancelBooking(currentUser, bookingReference);

            // ADD THIS: Send cancellation email
            emailService.sendBookingCancellationEmail(
                    currentUser.getEmail(),
                    cancelledBooking.getBookingReference(),
                    cancelledBooking.getFlightNumber(),
                    cancelledBooking.getDepartureAirportCode() + " → " + cancelledBooking.getArrivalAirportCode(),
                    cancelledBooking.getTotalPrice()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking cancelled successfully",
                    "data", Map.of(
                            "bookingReference", cancelledBooking.getBookingReference(),
                            "status", cancelledBooking.getStatus(),
                            "flightNumber", cancelledBooking.getFlightNumber(),
                            "route", cancelledBooking.getDepartureAirportCode() + " → " + cancelledBooking.getArrivalAirportCode(),
                            "refundAmount", cancelledBooking.getTotalPrice()
                    )
            ));
        } catch (Exception e) {
            log.error("Cancel booking error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
// =================
    // UTILITY ENDPOINTS
    // =================

    @GetMapping("/bookings/statistics")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getBookingStatistics(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            Map<String, Object> stats = bookingService.getBookingStatistics(currentUser);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking statistics retrieved successfully",
                    "data", stats
            ));
        } catch (Exception e) {
            log.error("Get booking statistics error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }


    @GetMapping("/flights/{flightId}/details")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getFlightDetails(@PathVariable Long flightId) {
        try {
            Optional<Flight> flight = flightService.getFlightById(flightId);

            if (flight.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Flight not found"));
            }

            Flight flightData = flight.get();

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("id", flightData.getId());
            data.put("flightNumber", flightData.getFlightNumber());
            data.put("airline", flightData.getAirline());
            data.put("departureAirport", Map.of(
                    "code", flightData.getDepartureAirport().getCode(),
                    "name", flightData.getDepartureAirport().getName(),
                    "city", flightData.getDepartureAirport().getCity()
            ));
            data.put("arrivalAirport", Map.of(
                    "code", flightData.getArrivalAirport().getCode(),
                    "name", flightData.getArrivalAirport().getName(),
                    "city", flightData.getArrivalAirport().getCity()
            ));
            data.put("departureTime", flightData.getDepartureTime());
            data.put("arrivalTime", flightData.getArrivalTime());
            data.put("price", flightData.getPrice());
            data.put("totalSeats", flightData.getTotalSeats());
            data.put("availableSeats", flightData.getAvailableSeats());
            data.put("status", flightData.getStatus());
            data.put("flightDurationHours", flightData.getFlightDurationHours());
            data.put("route", flightData.getDepartureAirport().getCode() + " → " + flightData.getArrivalAirport().getCode());
            data.put("isBookable", flightData.getStatus() == Flight.FlightStatus.SCHEDULED &&
                    flightData.getAvailableSeats() > 0 &&
                    flightData.getDepartureTime().isAfter(LocalDateTime.now()));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight details retrieved successfully",
                    "data", data
            ));
        } catch (Exception e) {
            log.error("Get flight details error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/airports/{code}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getAirportByCode(@PathVariable String code) {
        try {
            Airport airport = airportService.getAllAirports().stream()
                    .filter(a -> a.getCode().equalsIgnoreCase(code))
                    .findFirst()
                    .orElse(null);

            if (airport == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Airport not found with code: " + code));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airport details retrieved successfully",
                    "data", airport
            ));
        } catch (Exception e) {
            log.error("Get airport by code error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/popular-routes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getPopularRoutes() {
        try {
            List<Map<String, Object>> popularRoutes = List.of(
                    Map.of(
                            "route", "DEL → BOM",
                            "departure", "DEL",
                            "arrival", "BOM",
                            "departureName", "Delhi",
                            "arrivalName", "Mumbai",
                            "description", "Delhi to Mumbai"
                    ),
                    Map.of(
                            "route", "BOM → DEL",
                            "departure", "BOM",
                            "arrival", "DEL",
                            "departureName", "Mumbai",
                            "arrivalName", "Delhi",
                            "description", "Mumbai to Delhi"
                    ),
                    Map.of(
                            "route", "DEL → MAA",
                            "departure", "DEL",
                            "arrival", "MAA",
                            "departureName", "Delhi",
                            "arrivalName", "Chennai",
                            "description", "Delhi to Chennai"
                    ),
                    Map.of(
                            "route", "BOM → HYD",
                            "departure", "BOM",
                            "arrival", "HYD",
                            "departureName", "Mumbai",
                            "arrivalName", "Hyderabad",
                            "description", "Mumbai to Hyderabad"
                    ),
                    Map.of(
                            "route", "CCU → COH",
                            "departure", "CCU",
                            "arrival", "COH",
                            "departureName", "Kolkata",
                            "arrivalName", "Coimbatore",
                            "description", "Kolkata to Coimbatore"
                    ),
                    Map.of(
                            "route", "MAA → CCU",
                            "departure", "MAA",
                            "arrival", "CCU",
                            "departureName", "Chennai",
                            "arrivalName", "Kolkata",
                            "description", "Chennai to Kolkata"
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Popular routes retrieved successfully",
                    "totalRoutes", popularRoutes.size(),
                    "data", popularRoutes
            ));
        } catch (Exception e) {
            log.error("Get popular routes error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/availability-calendar")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getFlightAvailabilityCalendar(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam(defaultValue = "7") int days) {
        try {
            LocalDateTime startDate = LocalDateTime.now().toLocalDate().atStartOfDay();
            List<Map<String, Object>> calendar = new java.util.ArrayList<>();

            for (int i = 0; i < days; i++) {
                LocalDateTime checkDate = startDate.plusDays(i);

                try {
                    List<Flight> flights = bookingService.searchAvailableFlights(departure, arrival, checkDate);

                    calendar.add(Map.of(
                            "date", checkDate.toLocalDate(),
                            "dayOfWeek", checkDate.getDayOfWeek().name(),
                            "availableFlights", flights.size(),
                            "hasFlights", !flights.isEmpty(),
                            "minPrice", flights.isEmpty() ? null :
                                    flights.stream()
                                            .map(Flight::getPrice)
                                            .min(BigDecimal::compareTo)
                                            .orElse(null)
                    ));
                } catch (Exception e) {
                    calendar.add(Map.of(
                            "date", checkDate.toLocalDate(),
                            "dayOfWeek", checkDate.getDayOfWeek().name(),
                            "availableFlights", 0,
                            "hasFlights", false,
                            "minPrice", null
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight availability calendar retrieved successfully",
                    "searchCriteria", Map.of(
                            "departure", departure.toUpperCase(),
                            "arrival", arrival.toUpperCase(),
                            "days", days
                    ),
                    "data", calendar
            ));
        } catch (Exception e) {
            log.error("Get availability calendar error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/search")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> searchUserBookings(
            @RequestParam(required = false) String flightNumber,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");
            List<Booking> allBookings = bookingService.getUserBookings(currentUser);

            List<Booking> filteredBookings = allBookings.stream()
                    .filter(booking -> {
                        boolean matches = true;

                        if (flightNumber != null && !flightNumber.trim().isEmpty()) {
                            matches = booking.getFlightNumber().toLowerCase()
                                    .contains(flightNumber.toLowerCase());
                        }

                        if (matches && route != null && !route.trim().isEmpty()) {
                            String bookingRoute = booking.getDepartureAirportCode() + " → " + booking.getArrivalAirportCode();
                            matches = bookingRoute.toLowerCase().contains(route.toLowerCase());
                        }

                        if (matches && status != null && !status.trim().isEmpty()) {
                            matches = booking.getStatus().name().equalsIgnoreCase(status);
                        }

                        return matches;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking search completed successfully",
                    "searchCriteria", Map.of(
                            "flightNumber", flightNumber != null ? flightNumber : "",
                            "route", route != null ? route : "",
                            "status", status != null ? status : ""
                    ),
                    "totalResults", filteredBookings.size(),
                    "totalBookings", allBookings.size(),
                    "data", filteredBookings
            ));
        } catch (Exception e) {
            log.error("Search user bookings error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/help/booking-rules")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getBookingRules() {
        try {
            Map<String, Object> rules = Map.of(
                    "advanceBooking", Map.of(
                            "maximum", "1 month in advance",
                            "minimum", "Current time",
                            "description", "You can book flights up to 1 month ahead"
                    ),
                    "cancellation", Map.of(
                            "allowed", "Before flight departure",
                            "restriction", "Cannot cancel after departure",
                            "refund", "Full refund for cancelled bookings"
                    ),
                    "seatBooking", Map.of(
                            "minimum", 1,
                            "maximum", "Based on availability",
                            "description", "Book 1 or more seats per booking"
                    ),
                    "airportCodes", List.of("DEL", "BOM", "MAA", "CCU", "HYD", "COH"),
                    "flightOperations", Map.of(
                            "schedule", "24/7 continuous flights",
                            "routePattern", "Flights reverse routes automatically",
                            "availability", "Real-time seat updates"
                    ),
                    "bookingReference", Map.of(
                            "format", "FB + 9 digits",
                            "example", "FB123456789",
                            "usage", "Use for tracking and cancellation"
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking rules retrieved successfully",
                    "data", rules
            ));
        } catch (Exception e) {
            log.error("Get booking rules error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getSystemStatus() {
        try {
            LocalDateTime currentTime = LocalDateTime.now();

            List<Flight> allFlights = flightService.getAllFlights();
            long activeFlights = allFlights.stream()
                    .filter(f -> f.getStatus() != Flight.FlightStatus.CANCELLED)
                    .count();

            List<Airport> airports = airportService.getAllAirports();

            Map<String, Object> systemStatus = Map.of(
                    "serverTime", currentTime,
                    "systemStatus", "OPERATIONAL",
                    "flightOperations", Map.of(
                            "totalFlights", allFlights.size(),
                            "activeFlights", activeFlights,
                            "operationalMode", "24/7 Continuous"
                    ),
                    "airports", Map.of(
                            "totalAirports", airports.size(),
                            "availableCodes", List.of("DEL", "BOM", "MAA", "CCU", "HYD", "COH")
                    ),
                    "bookingSystem", Map.of(
                            "status", "AVAILABLE",
                            "advanceBookingLimit", "1 month",
                            "realTimeUpdates", true
                    ),
                    "features", List.of(
                            "Real-time flight search",
                            "Instant booking confirmation",
                            "Automatic seat management",
                            "24/7 flight operations",
                            "Booking history tracking",
                            "Cancellation support"
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "System status retrieved successfully",
                    "data", systemStatus
            ));
        } catch (Exception e) {
            log.error("Get system status error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    @GetMapping("/debug/flights/all")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getAllFlightsDebug() {
        try {
            List<Flight> allFlights = flightService.getAllFlights();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All flights retrieved",
                    "totalFlights", allFlights.size(),
                    "data", allFlights
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/debug/flights/route")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getFlightsByRoute(
            @RequestParam String departure,
            @RequestParam String arrival) {
        try {
            // This will help us see what flights exist for the route
            List<Flight> allFlights = flightService.getAllFlights();
            List<Flight> routeFlights = allFlights.stream()
                    .filter(f -> f.getDepartureAirport().getCode().equals(departure.toUpperCase()) &&
                            f.getArrivalAirport().getCode().equals(arrival.toUpperCase()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Route flights retrieved",
                    "route", departure + " -> " + arrival,
                    "totalFlights", routeFlights.size(),
                    "data", routeFlights
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    // Add this to your CustomerController class

    @PostMapping("/payments/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> verifyPayment(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            User currentUser = (User) httpRequest.getAttribute("currentUser");

            PaymentVerificationRequest verificationRequest = new PaymentVerificationRequest();
            verificationRequest.setOrderId(request.get("orderId"));
            verificationRequest.setRazorpayOrderId(request.get("razorpayOrderId"));
            verificationRequest.setRazorpayPaymentId(request.get("razorpayPaymentId"));
            verificationRequest.setRazorpaySignature(request.get("razorpaySignature"));

            PaymentVerificationResponse response = paymentService.verifyPayment(verificationRequest);

            return ResponseEntity.ok(Map.of(
                    "success", response.isSuccess(),
                    "message", response.getMessage(),
                    "data", response
            ));
        } catch (Exception e) {
            log.error("Error verifying payment", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/payments/status/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String orderId) {
        try {
            PaymentStatusResponse response = paymentService.getPaymentStatus(orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment status retrieved",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Add this endpoint to your CustomerController
    @PostMapping("/payments/verify-test")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> verifyPaymentTest(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        try {
            PaymentVerificationRequest verificationRequest = new PaymentVerificationRequest();
            verificationRequest.setOrderId(request.get("orderId"));
            verificationRequest.setRazorpayOrderId(request.get("razorpayOrderId"));
            verificationRequest.setRazorpayPaymentId(request.get("razorpayPaymentId"));
            verificationRequest.setRazorpaySignature(request.get("razorpaySignature"));

            PaymentVerificationResponse response = paymentService.verifyPaymentTest(verificationRequest);

            return ResponseEntity.ok(Map.of(
                    "success", response.isSuccess(),
                    "message", response.getMessage(),
                    "data", response
            ));
        } catch (Exception e) {
            log.error("Error verifying test payment", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/payments/history")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getPaymentHistory(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");

            List<Payment> paymentHistory = paymentService.getPaymentHistory(currentUser.getEmail());

            // Transform Payment entities to match your API response format
            List<Map<String, Object>> transformedData = paymentHistory.stream()
                    .map(payment -> new java.util.HashMap<String, Object>(Map.of(
                            "id", payment.getId(),
                            "orderId", payment.getOrderId(),
                            "razorpayOrderId", payment.getRazorpayOrderId() != null ? payment.getRazorpayOrderId() : "",
                            "razorpayPaymentId", payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : "",
                            "amount", payment.getAmount(),
                            "status", payment.getStatus().toString(),
                            "flightNumber", payment.getFlightNumber(),
                            "route", payment.getRoute(),
                            "passengerName", payment.getPassengerName(),
                            "createdAt", payment.getCreatedAt()
                    )))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment history retrieved successfully",
                    "totalPayments", paymentHistory.size(),
                    "data", transformedData
            ));
        } catch (Exception e) {
            log.error("Get payment history error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

}
