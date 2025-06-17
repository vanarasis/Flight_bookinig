package com.flightbooking.flightbooking.Repo;
import com.flightbooking.flightbooking.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySessionId(String sessionId);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.sessionId = null WHERE u.sessionId = :sessionId")
    void clearSessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE User u SET u.otpCode = null, u.otpExpiry = null WHERE u.email = :email")
    void clearOtpData(@Param("email") String email);

    // In your UserRepository interface
    List<User> findAll();
    List<User> findByRole(User.UserRole role);


}