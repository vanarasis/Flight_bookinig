package com.flightbooking.flightbooking.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    private String orderId;
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String flightNumber;
    private String route;
    private String departureTime;
    private String arrivalTime;
    private Integer seatsBooked;
    private String passengerName;
    private String razorpayKeyId; // Frontend needs this
}
