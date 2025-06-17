package com.flightbooking.flightbooking.Services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("SaiAirways - OTP Verification");
            message.setText("Dear User,\n\n" +
                    "Your OTP for verification is: " + otp + "\n\n" +
                    "This OTP will expire in 10 minutes.\n\n" +
                    "Please do not share this OTP with anyone.\n\n" +
                    "Thank you,\n" +
                    "SaiAirways Team");
            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending OTP email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send OTP email");
        }
    }

    public void sendWelcomeEmail(String toEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Welcome to SaiAirways!");
            message.setText("Dear Valued Customer,\n\n" +
                    "Welcome to SaiAirways!\n\n" +
                    "Thank you for registering with us. Your account has been successfully created and verified.\n\n" +
                    "You can now:\n" +
                    "• Search and book flights\n" +
                    "• Manage your bookings\n" +
                    "• Access exclusive deals and offers\n\n" +
                    "We look forward to serving you and making your travel experience memorable.\n\n" +
                    "Happy Flying!\n\n" +
                    "Best regards,\n" +
                    "SaiAirways Team");
            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending welcome email to: {}", toEmail, e);
        }
    }

    public void sendLoginOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("SaiAirways - Login Verification");
            message.setText("Dear User,\n\n" +
                    "Your login verification OTP is: " + otp + "\n\n" +
                    "This OTP will expire in 10 minutes.\n\n" +
                    "If you did not request this login, please ignore this email.\n\n" +
                    "Thank you,\n" +
                    "SaiAirways Team");
            mailSender.send(message);
            log.info("Login OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending login OTP email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send login OTP email");
        }
    }

    public void sendBookingConfirmationEmail(String toEmail, String bookingReference,
                                             String flightNumber, String route,
                                             LocalDateTime departureTime, LocalDateTime arrivalTime,
                                             String passengerName, Integer seatsBooked,
                                             BigDecimal totalAmount) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("SaiAirways - Booking Confirmation - " + bookingReference);

            String emailBody = buildBookingConfirmationEmail(bookingReference, flightNumber, route,
                    departureTime, arrivalTime, passengerName, seatsBooked, totalAmount);

            message.setText(emailBody);
            mailSender.send(message);
            log.info("Booking confirmation email sent successfully to: {} for booking: {}", toEmail, bookingReference);
        } catch (Exception e) {
            log.error("Error sending booking confirmation email to: {} for booking: {}", toEmail, bookingReference, e);
            throw new RuntimeException("Failed to send booking confirmation email");
        }
    }

    private String buildBookingConfirmationEmail(String bookingReference, String flightNumber,
                                                 String route, LocalDateTime departureTime,
                                                 LocalDateTime arrivalTime, String passengerName,
                                                 Integer seatsBooked, BigDecimal totalAmount) {
        StringBuilder emailBody = new StringBuilder();

        emailBody.append("🎉 BOOKING CONFIRMED! 🎉\n\n");
        emailBody.append("Dear ").append(passengerName).append(",\n\n");
        emailBody.append("Your flight booking has been successfully confirmed! Here are your booking details:\n\n");

        emailBody.append("📋 BOOKING DETAILS\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("Booking Reference: ").append(bookingReference).append("\n");
        emailBody.append("Passenger Name: ").append(passengerName).append("\n");
        emailBody.append("Seats Booked: ").append(seatsBooked).append("\n");
        emailBody.append("Total Amount Paid: ₹").append(totalAmount).append("\n\n");

        emailBody.append("✈️ FLIGHT DETAILS\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("Flight Number: ").append(flightNumber).append("\n");
        emailBody.append("Route: ").append(route).append("\n");
        emailBody.append("Departure: ").append(departureTime.format(fullFormatter)).append("\n");
        emailBody.append("Arrival: ").append(arrivalTime.format(fullFormatter)).append("\n\n");

        emailBody.append("📱 IMPORTANT INFORMATION\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("• Please arrive at the airport at least 2 hours before departure\n");
        emailBody.append("• Carry a valid government-issued photo ID\n");
        emailBody.append("• Keep this booking reference handy: ").append(bookingReference).append("\n");
        emailBody.append("• You can manage your booking anytime on our website\n\n");

        emailBody.append("🔄 MANAGE YOUR BOOKING\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("• View booking details\n");
        emailBody.append("• Cancel booking (if needed)\n");
        emailBody.append("• Download e-ticket\n");
        emailBody.append("• Check flight status\n\n");

        emailBody.append("📞 NEED HELP?\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("If you have any questions or need assistance, please contact us:\n");
        emailBody.append("• Customer Support: 1800-123-4567\n");
        emailBody.append("• Email: support@saiairways.com\n");
        emailBody.append("• Website: www.saiairways.com\n\n");

        emailBody.append("Thank you for choosing SaiAirways! We wish you a comfortable and pleasant journey.\n\n");
        emailBody.append("Safe travels! ✈️\n\n");
        emailBody.append("Best regards,\n");
        emailBody.append("SaiAirways Team\n");
        emailBody.append("\"Your journey, our priority\"\n\n");
        emailBody.append("═══════════════════════════════════════\n");
        emailBody.append("This is an automated email. Please do not reply to this email address.\n");

        return emailBody.toString();
    }

    public void sendPaymentFailureEmail(String toEmail, String orderId, String flightNumber,
                                        String route, BigDecimal amount, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("SaiAirways - Payment Failed - " + orderId);

            String emailBody = "Dear Customer,\n\n" +
                    "We regret to inform you that your payment for the following booking could not be processed:\n\n" +
                    "Order ID: " + orderId + "\n" +
                    "Flight: " + flightNumber + "\n" +
                    "Route: " + route + "\n" +
                    "Amount: ₹" + amount + "\n" +
                    "Reason: " + reason + "\n\n" +
                    "Please try booking again or contact our customer support for assistance.\n\n" +
                    "Customer Support: 1800-123-4567\n" +
                    "Email: support@saiairways.com\n\n" +
                    "Thank you for your understanding.\n\n" +
                    "Best regards,\n" +
                    "SaiAirways Team";

            message.setText(emailBody);
            mailSender.send(message);
            log.info("Payment failure email sent successfully to: {} for order: {}", toEmail, orderId);
        } catch (Exception e) {
            log.error("Error sending payment failure email to: {} for order: {}", toEmail, orderId, e);
        }
    }

    public void sendBookingCancellationEmail(String toEmail, String bookingReference,
                                             String flightNumber, String route,
                                             BigDecimal refundAmount) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("aparichitud000@gmail.com");
            message.setTo(toEmail);
            message.setSubject("SaiAirways - Booking Cancelled - " + bookingReference);

            String emailBody = "Dear Customer,\n\n" +
                    "Your booking has been successfully cancelled:\n\n" +
                    "Booking Reference: " + bookingReference + "\n" +
                    "Flight: " + flightNumber + "\n" +
                    "Route: " + route + "\n" +
                    "Refund Amount: ₹" + refundAmount + "\n\n" +
                    "The refund amount will be processed to your original payment method within 5-7 business days.\n\n" +
                    "If you have any questions, please contact our customer support:\n" +
                    "Customer Support: 1800-123-4567\n" +
                    "Email: support@saiairways.com\n\n" +
                    "Thank you for choosing SaiAirways.\n\n" +
                    "Best regards,\n" +
                    "SaiAirways Team";

            message.setText(emailBody);
            mailSender.send(message);
            log.info("Booking cancellation email sent successfully to: {} for booking: {}", toEmail, bookingReference);
        } catch (Exception e) {
            log.error("Error sending booking cancellation email to: {} for booking: {}", toEmail, bookingReference, e);
        }
    }

    // Add this method to your EmailService class
    public void sendTestEmail(String toEmail, String subject, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom("aparichitud000@gmail.com");
            mailMessage.setTo(toEmail);
            mailMessage.setSubject(subject);
            mailMessage.setText("ADMIN TEST EMAIL\n\n" + message + "\n\nSent from SaiAirways Admin Panel");

            mailSender.send(mailMessage);
            log.info("Test email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending test email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send test email: " + e.getMessage());
        }
    }
}
