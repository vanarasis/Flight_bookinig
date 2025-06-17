package com.flightbooking.flightbooking.Controllers;

import com.flightbooking.flightbooking.Services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> registerCustomer(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String password = request.get("password");

            if (email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Email and password are required"));
            }

            String result = authService.registerCustomer(email, password);
            return ResponseEntity.ok(Map.of("success", true, "message", result));

        } catch (Exception e) {
            log.error("Registration error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify-registration")
    public ResponseEntity<?> verifyRegistration(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");

            if (email == null || otp == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Email and OTP are required"));
            }

            String result = authService.verifyRegistrationOtp(email, otp);
            return ResponseEntity.ok(Map.of("success", true, "message", result));

        } catch (Exception e) {
            log.error("Registration verification error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> initiateLogin(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Email is required"));
            }

            String result = authService.initiateLogin(email);
            return ResponseEntity.ok(Map.of("success", true, "message", result));

        } catch (Exception e) {
            log.error("Login initiation error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify-login")
    public ResponseEntity<?> verifyLogin(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");

            if (email == null || otp == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Email and OTP are required"));
            }

            String token = authService.verifyLoginOtp(email, otp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Login successful",
                    "token", token
            ));

        } catch (Exception e) {
            log.error("Login verification error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid token"));
            }

            String token = authHeader.substring(7);
            // Extract session ID from token would need JWT utility
            // For now, we'll need to implement session validation

            return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid token"));
            }

            // Token validation logic would be implemented here
            return ResponseEntity.ok(Map.of("success", true, "message", "Token is valid"));

        } catch (Exception e) {
            log.error("Token validation error", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
