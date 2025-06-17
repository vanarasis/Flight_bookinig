package com.flightbooking.flightbooking.Entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId; // Our internal order ID

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(name = "passenger_phone", nullable = false)
    private String passengerPhone;

    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "failure_reason")
    private String failureReason;

    // Store flight details for reference
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

        // Generate internal order ID
        if (orderId == null) {
            generateOrderId();
        }

        // Store flight details
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

    private void generateOrderId() {
        // Generate format: ORDER_ + timestamp + random 4 digits
        String timestamp = String.valueOf(System.currentTimeMillis());
        String lastSix = timestamp.substring(timestamp.length() - 6);
        int random = (int) (Math.random() * 10000);
        this.orderId = "ORDER_" + lastSix + String.format("%04d", random);
    }

    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED,
        CANCELLED
    }

    // Add this method to your Payment entity
    public String getRoute() {
        return this.departureAirportCode + " → " + this.arrivalAirportCode;
    }
}
