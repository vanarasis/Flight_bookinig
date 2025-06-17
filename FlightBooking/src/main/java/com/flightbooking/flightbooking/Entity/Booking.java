package com.flightbooking.flightbooking.Entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(name = "passenger_email", nullable = false)
    private String passengerEmail;

    @Column(name = "passenger_phone")
    private String passengerPhone;

    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked = 1;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(name = "departure_airport_code", nullable = false)
    private String departureAirportCode;

    @Column(name = "arrival_airport_code", nullable = false)
    private String arrivalAirportCode;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "flight_number", nullable = false)
    private String flightNumber;

    @Column(name = "airline", nullable = false)
    private String airline;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        bookingDate = LocalDateTime.now();

        // Generate booking reference
        if (bookingReference == null) {
            generateBookingReference();
        }

        // Store flight details for historical reference
        if (flight != null) {
            this.departureAirportCode = flight.getDepartureAirport().getCode();
            this.arrivalAirportCode = flight.getArrivalAirport().getCode();
            this.departureTime = flight.getDepartureTime();
            this.arrivalTime = flight.getArrivalTime();
            this.flightNumber = flight.getFlightNumber();
            this.airline = flight.getAirline();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private void generateBookingReference() {
        // Generate format: FB + timestamp + random 3 digits
        String timestamp = String.valueOf(System.currentTimeMillis());
        String lastSix = timestamp.substring(timestamp.length() - 6);
        int random = (int) (Math.random() * 1000);
        this.bookingReference = "FB" + lastSix + String.format("%03d", random);
    }

    // Add these fields to your Booking.java entity
    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;


    public enum BookingStatus {
        CONFIRMED, CANCELLED, COMPLETED
    }
}
