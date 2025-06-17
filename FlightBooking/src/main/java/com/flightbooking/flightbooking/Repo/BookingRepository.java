package com.flightbooking.flightbooking.Repo;
import com.flightbooking.flightbooking.Entity.Booking;
import com.flightbooking.flightbooking.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Find booking by reference number
    Optional<Booking> findByBookingReference(String bookingReference);

    // Find all bookings for a specific user
    List<Booking> findByUserOrderByBookingDateDesc(User user);

    // Find user's upcoming bookings
    @Query("SELECT b FROM Booking b WHERE b.user = :user AND b.departureTime > :currentTime AND b.status = 'CONFIRMED' ORDER BY b.departureTime ASC")
    List<Booking> findUpcomingBookings(@Param("user") User user, @Param("currentTime") LocalDateTime currentTime);

    // Find user's past bookings
    @Query("SELECT b FROM Booking b WHERE b.user = :user AND b.departureTime <= :currentTime ORDER BY b.departureTime DESC")
    List<Booking> findPastBookings(@Param("user") User user, @Param("currentTime") LocalDateTime currentTime);

    // Check if booking reference exists
    boolean existsByBookingReference(String bookingReference);

    // Find bookings by flight ID (useful for admin)
    @Query("SELECT b FROM Booking b WHERE b.flight.id = :flightId ORDER BY b.bookingDate DESC")
    List<Booking> findBookingsByFlightId(@Param("flightId") Long flightId);

    // Count bookings for a specific flight
    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM Booking b WHERE b.flight.id = :flightId AND b.status = 'CONFIRMED'")
    Integer countBookedSeatsForFlight(@Param("flightId") Long flightId);

    // Find bookings between dates for reporting
    @Query("SELECT b FROM Booking b WHERE b.bookingDate BETWEEN :startDate AND :endDate ORDER BY b.bookingDate DESC")
    List<Booking> findBookingsBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}