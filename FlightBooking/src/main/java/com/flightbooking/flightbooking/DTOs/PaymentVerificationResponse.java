package com.flightbooking.flightbooking.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationResponse {
    private boolean success;
    private String message;
    private String bookingReference;
    private String orderId;
    private BigDecimal amount;
    private String flightNumber;
    private String route;
    private String status;
}
