package com.flightbooking.flightbooking.Services;
import com.flightbooking.flightbooking.Entity.User;
import com.flightbooking.flightbooking.Repo.UserRepository;
import com.flightbooking.flightbooking.Util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ADMIN_EMAIL = "aparichitud000@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123";



    public String registerCustomer(String email, String password) {
        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        // Create new customer
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(User.UserRole.CUSTOMER);
        user.setIsVerified(false);

        // Generate and set OTP
        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));

        userRepository.save(user);

        // Send OTP email
        emailService.sendOtpEmail(email, otp);

        log.info("Customer registration initiated for email: {}", email);
        return "Registration initiated. Please check your email for OTP verification.";
    }

    public String verifyRegistrationOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getIsVerified()) {
            throw new RuntimeException("User already verified");
        }

        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp)) {
            throw new RuntimeException("Invalid OTP");
        }

        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        // Verify user and clear OTP data
        user.setIsVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        // Send welcome email
        emailService.sendWelcomeEmail(email);

        log.info("Customer registration completed for email: {}", email);
        return "Registration completed successfully. Welcome email sent.";
    }

    public String initiateLogin(String email) {
        // Check if it's admin login
        if (ADMIN_EMAIL.equals(email)) {
            return initiateAdminLogin();
        }

        // Customer login
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getIsVerified()) {
            throw new RuntimeException("Please complete registration first");
        }

        if (user.getRole() != User.UserRole.CUSTOMER) {
            throw new RuntimeException("Invalid user type");
        }

        // Generate and send login OTP
        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendLoginOtpEmail(email, otp);

        log.info("Login OTP sent to customer: {}", email);
        return "Login OTP sent to your email";
    }

    private String initiateAdminLogin() {
        // Check if admin exists, if not create
        Optional<User> adminOpt = userRepository.findByEmail(ADMIN_EMAIL);
        User admin;

        if (adminOpt.isEmpty()) {
            admin = new User();
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setRole(User.UserRole.ADMIN);
            admin.setIsVerified(true);
        } else {
            admin = adminOpt.get();
        }

        // Generate and send login OTP
        String otp = generateOtp();
        admin.setOtpCode(otp);
        admin.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(admin);

        emailService.sendLoginOtpEmail(ADMIN_EMAIL, otp);

        log.info("Admin login OTP sent");
        return "Admin login OTP sent to your email";
    }

    public String verifyLoginOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp)) {
            throw new RuntimeException("Invalid OTP");
        }

        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(email, user.getRole().name());
        String sessionId = jwtUtil.extractSessionId(token);

        // Update user login info
        user.setSessionId(sessionId);
        user.setLastLogin(LocalDateTime.now());
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        log.info("User logged in successfully: {} with role: {}", email, user.getRole());
        return token;
    }

    public String logout(String sessionId) {
        User user = userRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Invalid session"));

        user.setSessionId(null);
        user.setLastLogout(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged out successfully: {}", user.getEmail());
        return "Logged out successfully";
    }

    // Additional method needed by CustomerController
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // Additional method to get user by email (throws exception if not found)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Method to check if user exists by email
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // Method to save/update user
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    // Method to get all users (for admin)
//    public List<User> getAllUsers() {
//        return userRepository.findAll();
//    }

    // Method to get all users by role
    public List<User> getUsersByRole(User.UserRole role) {
        return userRepository.findByRole(role);
    }

    // Method to get all customers only
    public List<User> getAllCustomers() {
        return userRepository.findByRole(User.UserRole.CUSTOMER);
    }



    public User validateSession(String sessionId) {
        return userRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Invalid or expired session"));
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    // Add this method to your AuthService class
//    public User getUserById(Long userId) {
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
//    }

//    public List<User> getAllUsers() {
//        return userRepository.findAll();
//    }


    // Add these methods to your AuthService class
    public List<User> getAllUsers() {
        try {
            return userRepository.findAll();
        } catch (Exception e) {
            log.error("Error getting all users", e);
            throw new RuntimeException("Failed to get users: " + e.getMessage());
        }
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }
}
