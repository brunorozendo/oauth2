package com.brunorozendo.oauth2.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
public class JwtAuthFilterConfig {

    @Bean
    public OncePerRequestFilter jwtAuthFilter(JwtConfig jwtConfig) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String token = extractToken(request, jwtConfig.getCookieName());

                if (token != null && jwtConfig.isTokenValid(token)) {
                    Claims claims = jwtConfig.parseToken(token);

                    JwtPrincipal principal = new JwtPrincipal(
                            claims.getSubject(),
                            (String) claims.get("email"),
                            (String) claims.get("name"),
                            (String) claims.get("picture"),
                            Boolean.TRUE.equals(claims.get("emailVerified")),
                            claims.getExpiration().getTime());

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    /**
     * Extracts the JWT from either:
     * 1. {@code Authorization: Bearer <token>} header — used by the Android app (OkHttp)
     * 2. The {@code AUTH_TOKEN} HttpOnly cookie — used by the web browser frontend
     */
    private String extractToken(HttpServletRequest request, String cookieName) {
        // 1. Bearer header (mobile)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. Cookie (web browser)
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
