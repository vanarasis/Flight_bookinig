package com.flightbooking.flightbooking.Config;
import com.flightbooking.flightbooking.Entity.User;
import com.flightbooking.flightbooking.Services.AuthService;
import com.flightbooking.flightbooking.Util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, @Lazy AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        if (requestPath.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String email = jwtUtil.extractEmail(token);
                String sessionId = jwtUtil.extractSessionId(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = authService.validateSession(sessionId);

                    if (jwtUtil.validateToken(token, email) && user.getEmail().equals(email)) {
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(email, null, List.of(authority));

                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        request.setAttribute("currentUser", user);
                        request.setAttribute("sessionId", sessionId);
                    }
                }
            } catch (Exception e) {
                log.error("JWT Authentication error: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired token\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
