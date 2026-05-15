package com.brunorozendo.oauth2.controller;

import com.brunorozendo.oauth2.config.AuthCodeStore;
import com.brunorozendo.oauth2.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth 2.1 PKCE code-exchange + refresh endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/auth/exchange} — caller posts {@code code} (received
 *       from the OAuth redirect) + {@code code_verifier} (kept since
 *       generating the corresponding {@code code_challenge}). Broker
 *       computes {@code base64url(SHA-256(code_verifier))}, compares with
 *       the stored challenge, returns {@code (accessToken, refreshToken,
 *       expiresAt)}. Code is single-use.</li>
 *   <li>{@code POST /api/auth/refresh} — caller posts {@code refreshToken}.
 *       Broker validates {@code typ=refresh} + signature + expiry, mints a
 *       new access + refresh pair (rotation). Returns same shape.</li>
 * </ul>
 *
 * <p>Both endpoints are permitAll (see {@code SecurityConfig}). The
 * caller's identity is established by possession of the code or refresh
 * token, not by an existing bearer header.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthExchangeController {

    private static final Logger logger = LoggerFactory.getLogger(AuthExchangeController.class);

    private final AuthCodeStore codes;
    private final JwtConfig jwt;

    public AuthExchangeController(AuthCodeStore codes, JwtConfig jwt) {
        this.codes = codes;
        this.jwt = jwt;
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody ExchangeRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()
                || req.codeVerifier() == null || req.codeVerifier().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_request",
                            "error_description", "code and code_verifier are required"));
        }
        AuthCodeStore.Entry entry = codes.consume(req.code());
        if (entry == null) {
            logger.info("auth/exchange: unknown or expired code");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant",
                            "error_description", "code is unknown or expired"));
        }
        if (!verifierMatches(entry, req.codeVerifier())) {
            logger.warn("auth/exchange: code_verifier mismatch for client={}", entry.clientId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant",
                            "error_description", "code_verifier does not match code_challenge"));
        }
        return ResponseEntity.ok(mintTokenPair(entry.userClaims()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        if (req == null || req.refreshToken() == null || req.refreshToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_request",
                            "error_description", "refresh_token is required"));
        }
        Claims claims;
        try {
            claims = jwt.parseRefreshToken(req.refreshToken());
        } catch (JwtException e) {
            logger.info("auth/refresh: bad token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_grant",
                            "error_description", "refresh token is invalid or expired"));
        }
        // Carry the profile claims forward into the new access + refresh
        // tokens. We re-build the map from individual gets so it stays
        // mutable for the JwtConfig.generate() implementation.
        Map<String, Object> userClaims = Map.of(
                "sub", claims.get("sub"),
                "email", claims.get("email"),
                "name", claims.get("name"),
                "picture", claims.get("picture"),
                "emailVerified", Boolean.TRUE.equals(claims.get("emailVerified")));
        return ResponseEntity.ok(mintTokenPair(userClaims));
    }

    private TokenResponse mintTokenPair(Map<String, Object> userClaims) {
        String accessToken = jwt.generateAccessToken(userClaims);
        String refreshToken = jwt.generateRefreshToken(userClaims);
        long expiresAt = Instant.now()
                .plusSeconds(jwt.getExpirationSeconds())
                .toEpochMilli();
        return new TokenResponse(accessToken, refreshToken, expiresAt, "Bearer");
    }

    private static boolean verifierMatches(AuthCodeStore.Entry entry, String verifier) {
        if (verifier == null || verifier.isBlank()) return false;
        // Only S256 is allowed (RFC 7636 §4.2). Reject "plain" — too weak.
        if (!"S256".equalsIgnoreCase(entry.codeChallengeMethod())) return false;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return MessageDigest.isEqual(
                    challenge.getBytes(StandardCharsets.US_ASCII),
                    entry.codeChallenge().getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from JRE", e);
        }
    }

    public record ExchangeRequest(
            String code,
            @com.fasterxml.jackson.annotation.JsonAlias({"code_verifier"})
            String codeVerifier) {}

    public record RefreshRequest(
            @com.fasterxml.jackson.annotation.JsonAlias({"refresh_token"})
            String refreshToken) {}

    public record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_at") long expiresAt,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType) {}
}
