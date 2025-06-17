package com.flightbooking.flightbooking.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private String orderId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String status;
    private BigDecimal amount;
    private String flightNumber;
    private String route;
    private String passengerName;
    private String createdAt;
    private String failureReason;
}
