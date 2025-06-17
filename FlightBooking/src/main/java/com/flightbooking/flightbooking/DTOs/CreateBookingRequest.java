package com.flightbooking.flightbooking.DTOs;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    private String passengerName;
    private String passengerEmail;
    private String passengerPhone;
    private Integer seatsBooked;
}
