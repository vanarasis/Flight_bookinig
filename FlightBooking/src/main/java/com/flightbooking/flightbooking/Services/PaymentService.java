package com.flightbooking.flightbooking.Services;
import com.flightbooking.flightbooking.DTOs.*;
import com.flightbooking.flightbooking.Entity.*;
import com.flightbooking.flightbooking.Repo.FlightRepository;
import com.flightbooking.flightbooking.Repo.PaymentRepository;
import com.flightbooking.flightbooking.Repo.UserRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.razorpay.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final FlightRepository flightRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final EmailService emailService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private RazorpayClient getRazorpayClient() throws RazorpayException {
        return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(PaymentOrderRequest request, String userEmail) {
        try {
            // Validate user
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Validate flight
            Flight flight = flightRepository.findById(request.getFlightId())
                    .orElseThrow(() -> new RuntimeException("Flight not found"));

            // Check flight availability using BookingService
            if (!bookingService.checkFlightAvailabilityForPayment(request.getFlightId(), request.getSeatsBooked())) {
                throw new RuntimeException("Flight is not available for booking or seats not sufficient");
            }

            // Calculate amount
            BigDecimal amount = flight.getPrice().multiply(BigDecimal.valueOf(request.getSeatsBooked()));

            // Create Razorpay order
            RazorpayClient razorpayClient = getRazorpayClient();
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue()); // Amount in paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "flight_booking_" + System.currentTimeMillis());

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            // Create payment record
            Payment payment = new Payment();
            payment.setUser(user);
            payment.setFlight(flight);
            payment.setPassengerName(request.getPassengerName());
            payment.setPassengerPhone(request.getPassengerPhone());
            payment.setSeatsBooked(request.getSeatsBooked());
            payment.setAmount(amount);
            payment.setRazorpayOrderId(razorpayOrder.get("id"));
            payment.setStatus(Payment.PaymentStatus.PENDING);

            payment = paymentRepository.save(payment);

            // Reserve seats using BookingService
            bookingService.reserveSeatsForPayment(request.getFlightId(), request.getSeatsBooked());

            log.info("Payment order created successfully. Order ID: {}, Razorpay Order ID: {}",
                    payment.getOrderId(), payment.getRazorpayOrderId());

            // Prepare response
            PaymentOrderResponse response = new PaymentOrderResponse();
            response.setOrderId(payment.getOrderId());
            response.setRazorpayOrderId(payment.getRazorpayOrderId());
            response.setAmount(amount);
            response.setCurrency("INR");
            response.setFlightNumber(flight.getFlightNumber());
            response.setRoute(flight.getDepartureAirport().getCode() + " → " + flight.getArrivalAirport().getCode());
            response.setDepartureTime(flight.getDepartureTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            response.setArrivalTime(flight.getArrivalTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            response.setSeatsBooked(request.getSeatsBooked());
            response.setPassengerName(request.getPassengerName());
            response.setRazorpayKeyId(razorpayKeyId);

            return response;

        } catch (RazorpayException e) {
            log.error("Error creating Razorpay order", e);
            throw new RuntimeException("Failed to create payment order: " + e.getMessage());
        }
    }

    // Replace your existing verifyPayment method in PaymentService with this production-ready version

    @Transactional
    public PaymentVerificationResponse verifyPayment(PaymentVerificationRequest request) {
        try {
            // Find payment by order ID
            Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Payment order not found"));

            // Verify the payment is still pending
            if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
                throw new RuntimeException("Payment is not in pending status. Current status: " + payment.getStatus());
            }

            // Verify signature using Razorpay Utils
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValidSignature;
            try {
                isValidSignature = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
            } catch (RazorpayException e) {
                log.error("Error verifying signature", e);
                isValidSignature = false;
            }

            if (isValidSignature) {
                // Payment successful
                payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
                payment.setRazorpaySignature(request.getRazorpaySignature());
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setPaymentMethod("Razorpay");
                paymentRepository.save(payment);

                // Create booking
                String bookingReference = createBookingAfterPayment(payment);

                // Send confirmation email
                sendBookingConfirmationEmail(payment, bookingReference);

                log.info("Payment verified successfully. Order ID: {}, Payment ID: {}, Booking Reference: {}",
                        payment.getOrderId(), request.getRazorpayPaymentId(), bookingReference);

                PaymentVerificationResponse response = new PaymentVerificationResponse();
                response.setSuccess(true);
                response.setMessage("Payment successful! Booking confirmed.");
                response.setBookingReference(bookingReference);
                response.setOrderId(payment.getOrderId());
                response.setAmount(payment.getAmount());
                response.setFlightNumber(payment.getFlightNumber());
                response.setRoute(payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode());
                response.setStatus("COMPLETED");

                return response;

            } else {
                // Payment verification failed
                payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason("Invalid signature - payment verification failed");
                paymentRepository.save(payment);

                // Release reserved seats
                releaseReservedSeats(payment);

                // Send failure email
                try {
                    emailService.sendPaymentFailureEmail(
                            payment.getUser().getEmail(),
                            payment.getOrderId(),
                            payment.getFlightNumber(),
                            payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode(),
                            payment.getAmount(),
                            "Payment signature verification failed"
                    );
                } catch (Exception e) {
                    log.error("Failed to send payment failure email", e);
                }

                log.warn("Payment verification failed. Order ID: {}, Payment ID: {}",
                        payment.getOrderId(), request.getRazorpayPaymentId());

                PaymentVerificationResponse response = new PaymentVerificationResponse();
                response.setSuccess(false);
                response.setMessage("Payment verification failed. Please contact support if amount was debited.");
                response.setOrderId(payment.getOrderId());
                response.setStatus("FAILED");

                return response;
            }

        } catch (Exception e) {
            log.error("Error verifying payment for order: {}", request.getOrderId(), e);

            PaymentVerificationResponse response = new PaymentVerificationResponse();
            response.setSuccess(false);
            response.setMessage("Payment verification failed: " + e.getMessage());
            response.setOrderId(request.getOrderId());
            response.setStatus("ERROR");

            return response;
        }
    }

    private String createBookingAfterPayment(Payment payment) {
        // Create booking using the new payment-optimized method
        CreateBookingRequest bookingRequest = new CreateBookingRequest();
        bookingRequest.setPassengerName(payment.getPassengerName());
        bookingRequest.setPassengerPhone(payment.getPassengerPhone());
        bookingRequest.setSeatsBooked(payment.getSeatsBooked());

        Booking booking = bookingService.createBookingAfterPayment(
                payment.getFlight().getId(),
                bookingRequest,
                payment.getUser()
        );
        return booking.getBookingReference();
    }

    private void releaseReservedSeats(Payment payment) {
        bookingService.releaseReservedSeats(payment.getFlight().getId(), payment.getSeatsBooked());
        log.info("Released {} seats for flight {}", payment.getSeatsBooked(), payment.getFlightNumber());
    }

    private void sendBookingConfirmationEmail(Payment payment, String bookingReference) {
        try {
            emailService.sendBookingConfirmationEmail(
                    payment.getUser().getEmail(),
                    bookingReference,
                    payment.getFlightNumber(),
                    payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode(),
                    payment.getDepartureTime(),
                    payment.getArrivalTime(),
                    payment.getPassengerName(),
                    payment.getSeatsBooked(),
                    payment.getAmount()
            );
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email", e);
        }
    }

    public PaymentStatusResponse getPaymentStatus(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setOrderId(payment.getOrderId());
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        response.setStatus(payment.getStatus().toString());
        response.setAmount(payment.getAmount());
        response.setFlightNumber(payment.getFlightNumber());
        response.setRoute(payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode());
        response.setPassengerName(payment.getPassengerName());
        response.setCreatedAt(payment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.setFailureReason(payment.getFailureReason());

        return response;
    }

    public List<Payment> getUserPayments(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return paymentRepository.findByUserOrderByCreatedAtDesc(user);
    }

    // Mark payment as failed and release seats
    @Transactional
    public void markPaymentAsFailed(String orderId, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);

        // Release reserved seats
        releaseReservedSeats(payment);

        // Send failure email
        try {
            emailService.sendPaymentFailureEmail(
                    payment.getUser().getEmail(),
                    payment.getOrderId(),
                    payment.getFlightNumber(),
                    payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode(),
                    payment.getAmount(),
                    reason
            );
        } catch (Exception e) {
            log.error("Failed to send payment failure email", e);
        }

        log.info("Payment marked as failed. Order ID: {}, Reason: {}", orderId, reason);
    }

//    // Process Razorpay webhook
//    @Transactional
//    public void processWebhook(String payload, String signature) {
//        try {
//            // Verify webhook signature
//            JSONObject event = new JSONObject(payload);
//            String eventType = event.getString("event");
//
//            if ("payment.captured".equals(eventType)) {
//                JSONObject paymentData = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
//                String razorpayPaymentId = paymentData.getString("id");
//                String razorpayOrderId = paymentData.getString("order_id");
//
//                // Find payment and update status
//                Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
//                if (paymentOpt.isPresent()) {
//                    Payment payment = paymentOpt.get();
//                    if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
//                        payment.setRazorpayPaymentId(razorpayPaymentId);
//                        payment.setStatus(Payment.PaymentStatus.COMPLETED);
//                        paymentRepository.save(payment);
//
//                        // Create booking
//                        String bookingReference = createBookingAfterPayment(payment);
//                        sendBookingConfirmationEmail(payment, bookingReference);
//
//                        log.info("Webhook processed successfully for payment: {}", payment.getOrderId());
//                    }
//                }
//            } else if ("payment.failed".equals(eventType)) {
//                JSONObject paymentData = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
//                String razorpayOrderId = paymentData.getString("order_id");
//
//                Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
//                if (paymentOpt.isPresent()) {
//                    Payment payment = paymentOpt.get();
//                    markPaymentAsFailed(payment.getOrderId(), "Payment failed via webhook");
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("Error processing webhook", e);
//            throw new RuntimeException("Webhook processing failed: " + e.getMessage());
//        }
//    }

    // Cleanup expired pending payments (can be called by scheduler)
    @Transactional
    public void cleanupExpiredPayments() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15); // 15 minutes ago
        List<Payment> expiredPayments = paymentRepository.findExpiredPendingPayments(cutoffTime);

        for (Payment payment : expiredPayments) {
            payment.setStatus(Payment.PaymentStatus.CANCELLED);
            payment.setFailureReason("Payment timeout");
            paymentRepository.save(payment);

            // Release reserved seats
            releaseReservedSeats(payment);

            log.info("Cancelled expired payment. Order ID: {}", payment.getOrderId());
        }
    }

    // Get recent payments (last 24 hours)
    public List<Payment> getRecentPayments() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return paymentRepository.findRecentPayments(since);
    }

    // Add this method to your PaymentService class
    @Transactional
    public PaymentVerificationResponse verifyPaymentTest(PaymentVerificationRequest request) {
        try {
            // Find payment by order ID
            Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Payment order not found"));

            // FOR TESTING: Skip signature verification
            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // Create booking
            String bookingReference = createBookingAfterPayment(payment);

            // Send confirmation email
            sendBookingConfirmationEmail(payment, bookingReference);

            log.info("TEST Payment verified successfully. Order ID: {}, Booking Reference: {}",
                    payment.getOrderId(), bookingReference);

            PaymentVerificationResponse response = new PaymentVerificationResponse();
            response.setSuccess(true);
            response.setMessage("Payment successful! Booking confirmed. (TEST MODE)");
            response.setBookingReference(bookingReference);
            response.setOrderId(payment.getOrderId());
            response.setAmount(payment.getAmount());
            response.setFlightNumber(payment.getFlightNumber());
            response.setRoute(payment.getDepartureAirportCode() + " → " + payment.getArrivalAirportCode());
            response.setStatus("COMPLETED");

            return response;

        } catch (Exception e) {
            log.error("Error in test payment verification", e);
            throw new RuntimeException("Failed to verify test payment: " + e.getMessage());
        }
    }

    // Add this method to your PaymentService class
    @Transactional
    public void processWebhook(String payload, String signature) {
        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");

            log.info("Processing webhook event: {}", eventType);

            if ("payment.captured".equals(eventType)) {
                JSONObject paymentEntity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String razorpayPaymentId = paymentEntity.getString("id");
                String razorpayOrderId = paymentEntity.getString("order_id");

                log.info("Payment captured webhook - Payment ID: {}, Order ID: {}", razorpayPaymentId, razorpayOrderId);

                // Find payment and update status
                Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
                if (paymentOpt.isPresent()) {
                    Payment payment = paymentOpt.get();
                    if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
                        payment.setRazorpayPaymentId(razorpayPaymentId);
                        payment.setStatus(Payment.PaymentStatus.COMPLETED);
                        payment.setPaymentMethod("Razorpay_Webhook");
                        paymentRepository.save(payment);

                        // Create booking
                        String bookingReference = createBookingAfterPayment(payment);
                        sendBookingConfirmationEmail(payment, bookingReference);

                        log.info("Webhook processed successfully for payment: {}, booking: {}", payment.getOrderId(), bookingReference);
                    } else {
                        log.info("Payment already processed: {}", payment.getOrderId());
                    }
                } else {
                    log.warn("Payment not found for Razorpay Order ID: {}", razorpayOrderId);
                }

            } else if ("payment.failed".equals(eventType)) {
                JSONObject paymentEntity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String razorpayOrderId = paymentEntity.getString("order_id");
                String errorDescription = paymentEntity.optString("error_description", "Payment failed");

                log.info("Payment failed webhook - Order ID: {}, Error: {}", razorpayOrderId, errorDescription);

                Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
                if (paymentOpt.isPresent()) {
                    Payment payment = paymentOpt.get();
                    markPaymentAsFailed(payment.getOrderId(), "Payment failed via webhook: " + errorDescription);
                }

            } else {
                log.info("Unhandled webhook event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            throw new RuntimeException("Webhook processing failed: " + e.getMessage());
        }
    }


    // Add these methods to your PaymentService class
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with order ID: " + orderId));
    }

    public Payment processAdminRefund(String orderId, String reason, BigDecimal refundAmount) {
        Payment payment = getPaymentByOrderId(orderId);

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed payments");
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setFailureReason("Admin refund: " + reason);

        log.info("Admin processed refund for order: {} with reason: {}", orderId, reason);
        return paymentRepository.save(payment);
    }

    public Map<String, Object> getRevenueAnalytics(String startDate, String endDate) {
        List<Payment> completedPayments = paymentRepository.findByStatus(Payment.PaymentStatus.COMPLETED);

        BigDecimal totalRevenue = completedPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> revenueByAirline = completedPayments.stream()
                .collect(Collectors.groupingBy(
                        Payment::getAirline,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));

        return Map.of(
                "totalRevenue", totalRevenue,
                "revenueByAirline", revenueByAirline,
                "totalCompletedPayments", completedPayments.size()
        );
    }

    public List<Payment> getPaymentHistory(String customerEmail) {
        try {
            User user = userRepository.findByEmail(customerEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return paymentRepository.findByUserOrderByCreatedAtDesc(user);

        } catch (Exception e) {
            log.error("Error getting payment history for customer: " + customerEmail, e);
            throw new RuntimeException("Failed to retrieve payment history: " + e.getMessage());
        }
    }

}
