package com.flightbooking.flightbooking.DTOs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Request DTO for creating payment order
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderRequest {
    private Long flightId;
    private String passengerName;
    private String passengerPhone;
    private Integer seatsBooked;
}
