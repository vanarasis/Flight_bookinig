package com.flightbooking.flightbooking.Controllers;
import com.flightbooking.flightbooking.Entity.*;
import com.flightbooking.flightbooking.Services.*;
import com.flightbooking.flightbooking.Util.FlightStatusScheduler;
import com.flightbooking.flightbooking.Util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AirportService airportService;
    private final FlightService flightService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final FlightStatusScheduler flightStatusScheduler;

    // =================
    // ADMIN DASHBOARD & AUTH
    // =================

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboard(HttpServletRequest request) {
        try {
            User currentUser = (User) request.getAttribute("currentUser");

            // Get system statistics
            List<Flight> allFlights = flightService.getAllFlights();
            List<User> allUsers = authService.getAllUsers();
            List<Airport> allAirports = airportService.getAllAirports();

            // Get payment statistics
            Map<String, Object> paymentStats = getPaymentStatistics();
            Map<String, Object> bookingStats = getSystemBookingStatistics();

            long customerCount = allUsers.stream()
                    .filter(user -> user.getRole() == User.UserRole.CUSTOMER)
                    .count();

            long activeFlights = allFlights.stream()
                    .filter(f -> f.getStatus() != Flight.FlightStatus.CANCELLED)
                    .count();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Welcome to Admin Dashboard",
                    "user", Map.of(
                            "email", currentUser.getEmail(),
                            "role", currentUser.getRole(),
                            "lastLogin", currentUser.getLastLogin()
                    ),
                    "systemStats", Map.of(
                            "totalCustomers", customerCount,
                            "totalFlights", allFlights.size(),
                            "activeFlights", activeFlights,
                            "totalAirports", allAirports.size()
                    ),
                    "paymentStats", paymentStats,
                    "bookingStats", bookingStats
            ));
        } catch (Exception e) {
            log.error("Admin dashboard error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean verified,
            HttpServletRequest request) {
        try {
            List<User> users = authService.getAllUsers();

            // Apply filters
            if (role != null) {
                users = users.stream()
                        .filter(user -> user.getRole().toString().equalsIgnoreCase(role))
                        .collect(Collectors.toList());
            }

            if (verified != null) {
                users = users.stream()
                        .filter(user -> user.getIsVerified().equals(verified))
                        .collect(Collectors.toList());
            }

            List<Map<String, Object>> userData = users.stream()
                    .map(user -> {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("email", user.getEmail());
                        userMap.put("role", user.getRole().toString());
                        userMap.put("isVerified", user.getIsVerified());
                        userMap.put("createdAt", user.getCreatedAt());
                        userMap.put("lastLogin", user.getLastLogin()); // Can be null
                        userMap.put("lastLogout", user.getLastLogout()); // Can be null
                        return userMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Users retrieved successfully");
            response.put("data", userData);
            response.put("totalUsers", userData.size());

            Map<String, Object> filters = new HashMap<>();
            filters.put("role", role != null ? role : "all");
            filters.put("verified", verified != null ? verified : "all");
            response.put("filters", filters);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Get users error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserDetails(@PathVariable Long userId) {
        try {
            User user = authService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "User not found"));
            }

            // Get user's booking statistics
            Map<String, Object> userBookingStats = bookingService.getBookingStatistics(user);

            // Get user's payment history
            List<Payment> userPayments = paymentService.getUserPayments(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "User details retrieved successfully",
                    "data", Map.of(
                            "id", user.getId(),
                            "email", user.getEmail(),
                            "role", user.getRole().toString(),
                            "isVerified", user.getIsVerified(),
                            "createdAt", user.getCreatedAt(),
                            "lastLogin", user.getLastLogin(),
                            "lastLogout", user.getLastLogout(),
                            "bookingStats", userBookingStats,
                            "totalPayments", userPayments.size()
                    )
            ));
        } catch (Exception e) {
            log.error("Get user details error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            String sessionId = (String) request.getAttribute("sessionId");
            String result = authService.logout(sessionId);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Admin logout error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // AIRPORT MANAGEMENT
    // =================

    @PostMapping("/airports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAirport(@RequestBody Airport airport) {
        try {
            Airport createdAirport = airportService.createAirport(airport);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airport created successfully",
                    "data", createdAirport
            ));
        } catch (Exception e) {
            log.error("Create airport error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/airports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllAirports() {
        try {
            List<Airport> airports = airportService.getAllAirports();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airports retrieved successfully",
                    "data", airports,
                    "totalAirports", airports.size()
            ));
        } catch (Exception e) {
            log.error("Get airports error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/airports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAirportById(@PathVariable Long id) {
        try {
            return airportService.getAirportById(id)
                    .map(airport -> ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Airport retrieved successfully",
                            "data", airport
                    )))
                    .orElse(ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Airport not found")));
        } catch (Exception e) {
            log.error("Get airport by id error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/airports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAirport(@PathVariable Long id, @RequestBody Airport airport) {
        try {
            Airport updatedAirport = airportService.updateAirport(id, airport);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airport updated successfully",
                    "data", updatedAirport
            ));
        } catch (Exception e) {
            log.error("Update airport error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/airports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAirport(@PathVariable Long id) {
        try {
            airportService.deleteAirport(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Airport deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Delete airport error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // FLIGHT MANAGEMENT
    // =================

    @PostMapping("/flights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createFlight(@RequestBody Flight flight) {
        try {
            Flight createdFlight = flightService.createFlight(flight);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight created successfully",
                    "data", createdFlight
            ));
        } catch (Exception e) {
            log.error("Create flight error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllFlights(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String airline) {
        try {
            List<Flight> flights = flightService.getAllFlights();

            // Apply filters
            if (status != null) {
                flights = flights.stream()
                        .filter(f -> f.getStatus().toString().equalsIgnoreCase(status))
                        .collect(Collectors.toList());
            }

            if (airline != null) {
                flights = flights.stream()
                        .filter(f -> f.getAirline().toLowerCase().contains(airline.toLowerCase()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flights retrieved successfully",
                    "data", flights,
                    "totalFlights", flights.size(),
                    "filters", Map.of(
                            "status", status != null ? status : "all",
                            "airline", airline != null ? airline : "all"
                    )
            ));
        } catch (Exception e) {
            log.error("Get flights error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFlightById(@PathVariable Long id) {
        try {
            return flightService.getFlightById(id)
                    .map(flight -> ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Flight retrieved successfully",
                            "data", flight
                    )))
                    .orElse(ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Flight not found")));
        } catch (Exception e) {
            log.error("Get flight by id error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/flights/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateFlight(@PathVariable Long id, @RequestBody Flight flight) {
        try {
            Flight updatedFlight = flightService.updateFlight(id, flight);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight updated successfully",
                    "data", updatedFlight
            ));
        } catch (Exception e) {
            log.error("Update flight error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/flights/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFlight(@PathVariable Long id) {
        try {
            flightService.deleteFlight(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Delete flight error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // BOOKING MANAGEMENT
    // =================

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String flightNumber,
            @RequestParam(required = false) String userEmail) {
        try {
            List<Booking> allBookings = bookingService.getAllBookings();

            // Apply filters
            if (status != null) {
                allBookings = allBookings.stream()
                        .filter(b -> b.getStatus().toString().equalsIgnoreCase(status))
                        .collect(Collectors.toList());
            }

            if (flightNumber != null) {
                allBookings = allBookings.stream()
                        .filter(b -> b.getFlightNumber().toLowerCase().contains(flightNumber.toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (userEmail != null) {
                allBookings = allBookings.stream()
                        .filter(b -> b.getUser().getEmail().toLowerCase().contains(userEmail.toLowerCase()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All bookings retrieved successfully",
                    "data", allBookings,
                    "totalBookings", allBookings.size(),
                    "filters", Map.of(
                            "status", status != null ? status : "all",
                            "flightNumber", flightNumber != null ? flightNumber : "all",
                            "userEmail", userEmail != null ? userEmail : "all"
                    )
            ));
        } catch (Exception e) {
            log.error("Get all bookings error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/{bookingReference}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getBookingByReference(@PathVariable String bookingReference) {
        try {
            Booking booking = bookingService.getBookingByReference(bookingReference)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking details retrieved successfully",
                    "data", booking
            ));
        } catch (Exception e) {
            log.error("Get booking by reference error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // In your admin cancellation method
    @PostMapping("/bookings/{bookingReference}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminCancelBooking(
            @PathVariable String bookingReference,
            @RequestBody Map<String, String> requestBody) {
        try {
            // Get reason from request body
            String reason = requestBody.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                reason = "Cancelled by admin";
            }

            Booking cancelledBooking = bookingService.adminCancelBooking(bookingReference, reason);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking cancelled successfully by admin",
                    "data", Map.of(
                            "id", cancelledBooking.getId(),
                            "bookingReference", cancelledBooking.getBookingReference(),
                            "status", cancelledBooking.getStatus().toString(),
                            "passengerName", cancelledBooking.getPassengerName(),
                            "totalPrice", cancelledBooking.getTotalPrice(),
                            "refundAmount", cancelledBooking.getTotalPrice(), // Same as total price
                            "cancellationReason", cancelledBooking.getCancellationReason(),
                            "cancelledAt", cancelledBooking.getCancelledAt()
                    )
            ));
        } catch (Exception e) {
            log.error("Admin cancel booking error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // PAYMENT MANAGEMENT
    // =================

    @GetMapping("/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userEmail) {
        try {
            List<Payment> allPayments = paymentService.getAllPayments();

            // Apply filters
            if (status != null) {
                allPayments = allPayments.stream()
                        .filter(p -> p.getStatus().toString().equalsIgnoreCase(status))
                        .collect(Collectors.toList());
            }

            if (userEmail != null) {
                allPayments = allPayments.stream()
                        .filter(p -> p.getUser().getEmail().toLowerCase().contains(userEmail.toLowerCase()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All payments retrieved successfully",
                    "data", allPayments,
                    "totalPayments", allPayments.size(),
                    "filters", Map.of(
                            "status", status != null ? status : "all",
                            "userEmail", userEmail != null ? userEmail : "all"
                    )
            ));
        } catch (Exception e) {
            log.error("Get all payments error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/payments/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPaymentByOrderId(@PathVariable String orderId) {
        try {
            Payment payment = paymentService.getPaymentByOrderId(orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment details retrieved successfully",
                    "data", payment
            ));
        } catch (Exception e) {
            log.error("Get payment by order ID error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/payments/{orderId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> processRefund(@PathVariable String orderId,
                                           @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "Refunded by admin");
            BigDecimal refundAmount = request.get("refundAmount") != null ?
                    new BigDecimal(request.get("refundAmount")) : null;

            Payment refundedPayment = paymentService.processAdminRefund(orderId, reason, refundAmount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Refund processed successfully",
                    "data", refundedPayment
            ));
        } catch (Exception e) {
            log.error("Process refund error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // ANALYTICS & REPORTS
    // =================

    @GetMapping("/analytics/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRevenueAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> revenueData = paymentService.getRevenueAnalytics(startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Revenue analytics retrieved successfully",
                    "data", revenueData
            ));
        } catch (Exception e) {
            log.error("Get revenue analytics error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/analytics/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getBookingAnalytics() {
        try {
            Map<String, Object> bookingAnalytics = bookingService.getBookingAnalytics();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Booking analytics retrieved successfully",
                    "data", bookingAnalytics
            ));
        } catch (Exception e) {
            log.error("Get booking analytics error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/analytics/popular-routes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPopularRoutesAnalytics() {
        try {
            Map<String, Object> routeAnalytics = bookingService.getPopularRoutesAnalytics();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Popular routes analytics retrieved successfully",
                    "data", routeAnalytics
            ));
        } catch (Exception e) {
            log.error("Get popular routes analytics error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // FLIGHT STATUS MANAGEMENT
    // =================

    @PostMapping("/flights/{id}/update-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateFlightStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String statusStr = request.get("status");
            Flight.FlightStatus status = Flight.FlightStatus.valueOf(statusStr.toUpperCase());

            Flight flight = flightService.getFlightById(id)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            Flight updatedFlight = new Flight();
            updatedFlight.setStatus(status);
            updatedFlight.setFlightNumber(flight.getFlightNumber());
            updatedFlight.setDepartureAirport(flight.getDepartureAirport());
            updatedFlight.setArrivalAirport(flight.getArrivalAirport());
            updatedFlight.setDepartureTime(flight.getDepartureTime());
            updatedFlight.setArrivalTime(flight.getArrivalTime());
            updatedFlight.setAirline(flight.getAirline());
            updatedFlight.setPrice(flight.getPrice());
            updatedFlight.setTotalSeats(flight.getTotalSeats());
            updatedFlight.setAvailableSeats(flight.getAvailableSeats());

            Flight result = flightService.updateFlight(id, updatedFlight);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight status updated successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Update flight status error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/flights/{id}/update-arrival-time")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateArrivalTime(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String arrivalTimeStr = request.get("arrivalTime");
            LocalDateTime arrivalTime = LocalDateTime.parse(arrivalTimeStr);

            Flight flight = flightService.getFlightById(id)
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            Flight updatedFlight = new Flight();
            updatedFlight.setFlightNumber(flight.getFlightNumber());
            updatedFlight.setDepartureAirport(flight.getDepartureAirport());
            updatedFlight.setArrivalAirport(flight.getArrivalAirport());
            updatedFlight.setDepartureTime(flight.getDepartureTime());
            updatedFlight.setArrivalTime(arrivalTime);
            updatedFlight.setAirline(flight.getAirline());
            updatedFlight.setPrice(flight.getPrice());
            updatedFlight.setTotalSeats(flight.getTotalSeats());
            updatedFlight.setAvailableSeats(flight.getAvailableSeats());
            updatedFlight.setStatus(flight.getStatus());

            Flight result = flightService.updateFlight(id, updatedFlight);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight arrival time updated successfully",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("Update arrival time error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/flights/update-statuses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerStatusUpdate() {
        try {
            String result = flightStatusScheduler.updateFlightStatusesManually();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result
            ));
        } catch (Exception e) {
            log.error("Manual status update error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/current-time")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCurrentTime() {
        try {
            ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "currentTimeIST", istTime.toLocalDateTime(),
                    "timezone", "Asia/Kolkata"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/cycle-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFlightCycleStats() {
        try {
            String stats = flightStatusScheduler.getFlightCycleStats();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight cycle statistics retrieved",
                    "data", stats
            ));
        } catch (Exception e) {
            log.error("Get cycle stats error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/{id}/cycle-info")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFlightCycleInfo(@PathVariable Long id) {
        try {
            String info = flightService.getFlightCycleInfo(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flight cycle info retrieved",
                    "data", info
            ));
        } catch (Exception e) {
            log.error("Get flight cycle info error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/flights/{id}/reset-cycles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetFlightCycles(@PathVariable Long id) {
        try {
            String result = flightService.resetFlightCycleCount(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result
            ));
        } catch (Exception e) {
            log.error("Reset flight cycles error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/flights/active-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getActiveFlightStatus() {
        try {
            List<Flight> flights = flightService.getAllFlights();
            ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
            LocalDateTime currentTime = istTime.toLocalDateTime();

            List<Map<String, Object>> flightStatus = flights.stream()
                    .filter(f -> f.getStatus() != Flight.FlightStatus.CANCELLED)
                    .map(f -> Map.<String, Object>of(
                            "flightNumber", f.getFlightNumber(),
                            "status", f.getStatus().toString(),
                            "currentRoute", f.getDepartureAirport().getCode() + " → " + f.getArrivalAirport().getCode(),
                            "originalRoute", f.getOriginalDepartureAirport().getCode() + " → " + f.getOriginalArrivalAirport().getCode(),
                            "isReversed", f.getIsRouteReversed(),
                            "cycleCount", f.getCycleCount(),
                            "departureTime", f.getDepartureTime(),
                            "arrivalTime", f.getArrivalTime(),
                            "nextDepartureTime", f.getNextDepartureTime(),
                            "lastCycleReset", f.getLastCycleReset()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "currentTime", currentTime,
                    "timezone", "Asia/Kolkata",
                    "totalActiveFlights", flightStatus.size(),
                    "data", flightStatus
            ));
        } catch (Exception e) {
            log.error("Get active flight status error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/trigger-scheduler")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerSchedulerMultipleTimes() {
        try {
            for (int i = 0; i < 50; i++) {
                flightStatusScheduler.updateFlightStatuses();
                log.info("Manual scheduler trigger #{}", i + 1);
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Scheduler triggered 50 times - flights should now be available for future dates"
            ));
        } catch (Exception e) {
            log.error("Error triggering scheduler", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // SYSTEM MANAGEMENT
    // =================

    @PostMapping("/system/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cleanupExpiredPayments() {
        try {
            paymentService.cleanupExpiredPayments();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Expired payments cleaned up successfully"
            ));
        } catch (Exception e) {
            log.error("Cleanup expired payments error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/system/send-test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendTestEmail(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String subject = request.getOrDefault("subject", "Test Email from SaiAirways Admin");
            String message = request.getOrDefault("message", "This is a test email sent by admin.");

            emailService.sendTestEmail(email, subject, message);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test email sent successfully to " + email
            ));
        } catch (Exception e) {
            log.error("Send test email error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getSystemLogs(@RequestParam(defaultValue = "100") int limit) {
        try {
            // This would typically read from log files
            // For now, return a placeholder response
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "System logs retrieved successfully",
                    "data", "Log functionality to be implemented - check server logs for detailed information",
                    "limit", limit
            ));
        } catch (Exception e) {
            log.error("Get system logs error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =================
    // HELPER METHODS
    // =================

    private Map<String, Object> getPaymentStatistics() {
        try {
            List<Payment> allPayments = paymentService.getAllPayments();

            long completedPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .count();

            long pendingPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                    .count();

            long failedPayments = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED)
                    .count();

            BigDecimal totalRevenue = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return Map.of(
                    "totalPayments", allPayments.size(),
                    "completedPayments", completedPayments,
                    "pendingPayments", pendingPayments,
                    "failedPayments", failedPayments,
                    "totalRevenue", totalRevenue
            );
        } catch (Exception e) {
            log.error("Error getting payment statistics", e);
            return Map.of(
                    "totalPayments", 0,
                    "completedPayments", 0,
                    "pendingPayments", 0,
                    "failedPayments", 0,
                    "totalRevenue", BigDecimal.ZERO
            );
        }
    }

    private Map<String, Object> getSystemBookingStatistics() {
        try {
            List<Booking> allBookings = bookingService.getAllBookings();

            long confirmedBookings = allBookings.stream()
                    .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                    .count();

            long cancelledBookings = allBookings.stream()
                    .filter(b -> b.getStatus() == Booking.BookingStatus.CANCELLED)
                    .count();

            long completedBookings = allBookings.stream()
                    .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
                    .count();

            return Map.of(
                    "totalBookings", allBookings.size(),
                    "confirmedBookings", confirmedBookings,
                    "cancelledBookings", cancelledBookings,
                    "completedBookings", completedBookings
            );
        } catch (Exception e) {
            log.error("Error getting booking statistics", e);
            return Map.of(
                    "totalBookings", 0,
                    "confirmedBookings", 0,
                    "cancelledBookings", 0,
                    "completedBookings", 0
            );
        }
    }


    @GetMapping("/test-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testGetUsers() {
        try {
            List<User> users = authService.getAllUsers();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test successful",
                    "userCount", users.size()
            ));
        } catch (Exception e) {
            log.error("Test users error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
