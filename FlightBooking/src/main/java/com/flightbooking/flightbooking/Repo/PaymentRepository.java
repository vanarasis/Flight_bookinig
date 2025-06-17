package com.flightbooking.flightbooking.Repo;
import com.flightbooking.flightbooking.Entity.Payment;
import com.flightbooking.flightbooking.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Find payment by internal order ID
    Optional<Payment> findByOrderId(String orderId);

    // Find payment by Razorpay order ID
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    // Find payment by Razorpay payment ID
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    // Find all payments by user
    List<Payment> findByUserOrderByCreatedAtDesc(User user);

    // Find payments by status
    List<Payment> findByStatus(Payment.PaymentStatus status);

    // Find user's payments by status
    List<Payment> findByUserAndStatusOrderByCreatedAtDesc(User user, Payment.PaymentStatus status);

    // Count payments by status
    long countByStatus(Payment.PaymentStatus status);

    // Count user's payments
    long countByUser(User user);

    // Find pending payments older than specified minutes (for cleanup)
    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.createdAt < :cutoffTime")
    List<Payment> findExpiredPendingPayments(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Get payment statistics
    @Query("SELECT p.status, COUNT(p) FROM Payment p GROUP BY p.status")
    List<Object[]> getPaymentStatistics();

    // Find recent payments (last 24 hours) - Fixed query
    @Query("SELECT p FROM Payment p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Payment> findRecentPayments(@Param("since") LocalDateTime since);
}