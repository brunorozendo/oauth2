package com.brunorozendo.oauth2.controller;

import com.brunorozendo.oauth2.config.JwtConfig;
import com.brunorozendo.oauth2.config.JwtPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class OAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);

    private final JwtConfig jwtConfig;

    public OAuth2Controller(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();

        Map<String, Object> response = Map.of(
                "sub", principal.getSub(),
                "email", principal.getEmail(),
                "name", principal.getName(),
                "picture", principal.getPicture(),
                "emailVerified", principal.isEmailVerified(),
                "expiresAt", principal.getExpiresAtEpochMilli());

        logger.debug("User info returned for: {}", principal.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(Authentication authentication,
                                          HttpServletResponse response) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();

        Map<String, Object> claims = Map.of(
                "sub", principal.getSub(),
                "email", principal.getEmail(),
                "name", principal.getName(),
                "picture", principal.getPicture(),
                "emailVerified", principal.isEmailVerified());

        String token = jwtConfig.generateToken(claims);
        response.addCookie(jwtConfig.createAuthCookie(token));

        long newExpiresAt = Instant.now()
                .plusSeconds(jwtConfig.getExpirationSeconds())
                .toEpochMilli();

        logger.debug("JWT refreshed for user: {}", principal.getEmail());

        return ResponseEntity.ok(Map.of(
                "expiresAt", newExpiresAt,
                "message", "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        response.addCookie(jwtConfig.createClearCookie());
        logger.debug("User logged out, JWT cookie cleared");

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
