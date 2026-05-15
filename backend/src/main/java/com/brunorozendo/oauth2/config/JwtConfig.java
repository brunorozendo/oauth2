package com.brunorozendo.oauth2.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT minting and verification.
 *
 * <p>Issues two flavours of token, both HS256-signed with the same shared
 * secret (so downstream services that already verify the access token need
 * no key rotation):
 * <ul>
 *   <li><b>Access token</b> — short-lived (default 1h). Carries the full
 *       user profile claims (sub, email, name, picture, emailVerified).
 *       Tagged with {@code typ:"access"}.</li>
 *   <li><b>Refresh token</b> — long-lived (default 30d). Carries the same
 *       profile claims so the refresh endpoint can stamp a fresh access
 *       token without an extra DB lookup. Tagged with {@code typ:"refresh"}
 *       so downstream services (anotadissimo backend) can reject it on
 *       protected endpoints.</li>
 * </ul>
 */
@Configuration
public class JwtConfig {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final int accessExpirationSeconds;
    private final int refreshExpirationSeconds;
    private final String cookieName;

    public JwtConfig(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") int accessExpirationSeconds,
            @Value("${app.jwt.refresh-expiration-seconds:2592000}") int refreshExpirationSeconds,
            @Value("${app.jwt.cookie-name}") String cookieName) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
        this.cookieName = cookieName;
    }

    /** Mints an access token. Claims may NOT contain {@code typ} (we set it). */
    public String generateAccessToken(Map<String, Object> claims) {
        return generate(claims, TYPE_ACCESS, accessExpirationSeconds);
    }

    /** Mints a refresh token carrying the same profile claims. */
    public String generateRefreshToken(Map<String, Object> claims) {
        return generate(claims, TYPE_REFRESH, refreshExpirationSeconds);
    }

    /** @deprecated Use {@link #generateAccessToken(Map)}. Kept for callers
     *  that pre-date the access/refresh split; identical behaviour today. */
    @Deprecated
    public String generateToken(Map<String, Object> claims) {
        return generateAccessToken(claims);
    }

    private String generate(Map<String, Object> baseClaims, String type, int expirationSeconds) {
        Map<String, Object> claims = new LinkedHashMap<>(baseClaims);
        claims.put("typ", type);
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Parses + asserts {@code typ:"refresh"}. Throws {@link JwtException}
     *  if the token isn't a refresh token (or signature/expiry fails). */
    public Claims parseRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (!TYPE_REFRESH.equals(claims.get("typ"))) {
            throw new JwtException("not a refresh token (typ=" + claims.get("typ") + ")");
        }
        return claims;
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Cookie createAuthCookie(String token) {
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(accessExpirationSeconds);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public Cookie createClearCookie() {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public String getCookieName() {
        return cookieName;
    }

    public int getExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public int getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }
}
