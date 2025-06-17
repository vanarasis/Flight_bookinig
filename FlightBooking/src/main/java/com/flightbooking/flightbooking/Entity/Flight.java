package com.flightbooking.flightbooking.Entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flights")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departure_airport_id", nullable = false)
    private Airport departureAirport;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "arrival_airport_id", nullable = false)
    private Airport arrivalAirport;

    // Store original airports for route reversal
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "original_departure_airport_id", nullable = false)
    private Airport originalDepartureAirport;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "original_arrival_airport_id", nullable = false)
    private Airport originalArrivalAirport;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(nullable = false)
    private String airline;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status = FlightStatus.SCHEDULED;

    // Cycle tracking fields
    @Column(name = "cycle_count", nullable = false)
    private Integer cycleCount = 0;

    @Column(name = "last_cycle_reset")
    private LocalDateTime lastCycleReset;

    @Column(name = "flight_duration_hours", nullable = false)
    private Double flightDurationHours = 2.5; // Default 2.5 hours

    @Column(name = "ground_time_hours", nullable = false)
    private Double groundTimeHours = 1.0; // Default 1 hour ground time

    @Column(name = "next_departure_time")
    private LocalDateTime nextDepartureTime;

    @Column(name = "is_route_reversed", nullable = false)
    private Boolean isRouteReversed = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastCycleReset = LocalDateTime.now();
        if (availableSeats == null) {
            availableSeats = totalSeats;
        }
        // Set original airports for route tracking
        if (originalDepartureAirport == null) {
            originalDepartureAirport = departureAirport;
        }
        if (originalArrivalAirport == null) {
            originalArrivalAirport = arrivalAirport;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FlightStatus {
        SCHEDULED, CANCELLED, FLYING, COMPLETED
    }

    // Helper method to check if 24 hours passed since last cycle reset
    public boolean shouldResetCycleCount() {
        if (lastCycleReset == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(lastCycleReset.plusHours(24));
    }

    // Helper method to reset cycle count
    public void resetCycleCount() {
        this.cycleCount = 0;
        this.lastCycleReset = LocalDateTime.now();
    }

    // Helper method to increment cycle count
    public void incrementCycleCount() {
        this.cycleCount++;
    }

    // Helper method to reverse route
    public void reverseRoute() {
        Airport temp = this.departureAirport;
        this.departureAirport = this.arrivalAirport;
        this.arrivalAirport = temp;
        this.isRouteReversed = !this.isRouteReversed;
    }

    // Helper method to calculate next flight times
    public void calculateNextFlightTimes() {
        LocalDateTime nextDep = this.arrivalTime.plusHours(groundTimeHours.longValue());
        this.nextDepartureTime = nextDep;
        this.departureTime = nextDep;
        this.arrivalTime = nextDep.plusHours(flightDurationHours.longValue());
    }
}
