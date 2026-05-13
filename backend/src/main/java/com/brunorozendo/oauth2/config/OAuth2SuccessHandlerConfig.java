package com.brunorozendo.oauth2.config;

import com.brunorozendo.oauth2.controller.MobileAuthInitController;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
public class OAuth2SuccessHandlerConfig {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandlerConfig.class);

    @Bean
    public AuthenticationSuccessHandler oAuth2SuccessHandler(
            JwtConfig jwtConfig,
            @Value("${app.frontend.origin}") String frontendOrigin,
            @Value("${app.mobile.redirect-uris.inventmove:}") String inventmoveUri,
            @Value("${app.mobile.redirect-uris.bossbill:}") String bossbillUri,
            @Value("${app.mobile.redirect-uris.comprasia:}") String comprasiaUri,
            @Value("${app.mobile.redirect-uris.comprasia-ios:}") String comprasiaIosUri) {

        Map<String, String> mobileRedirects = new java.util.HashMap<>();
        if (!inventmoveUri.isBlank()) mobileRedirects.put("inventmove", inventmoveUri);
        if (!bossbillUri.isBlank()) mobileRedirects.put("bossbill", bossbillUri);
        if (!comprasiaUri.isBlank()) mobileRedirects.put("comprasia", comprasiaUri);
        if (!comprasiaIosUri.isBlank()) mobileRedirects.put("comprasia-ios", comprasiaIosUri);

        return (HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) -> {

            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oauth2User.getAttributes();

            String email   = (String) attributes.getOrDefault("email",          "unknown@example.com");
            String name    = (String) attributes.getOrDefault("name",            "Unknown User");
            String picture = (String) attributes.getOrDefault("picture",         "/assets/default-avatar.png");
            Object sub     = attributes.getOrDefault("sub",                      "unknown");
            Object emailOk = attributes.getOrDefault("email_verified",           false);

            Map<String, Object> claims = Map.of(
                    "sub",           sub,
                    "email",         email,
                    "name",          name,
                    "picture",       picture,
                    "emailVerified", emailOk);

            String token = jwtConfig.generateToken(claims);

            logger.debug("JWT issued for user: {}", email);

            // Prefer the dedicated mobile-client cookie (set by MobileAuthInitController) —
            // it survives the OAuth round-trip even when the user is already authenticated
            // via the AUTH_TOKEN cookie from a prior web login. Falls back to the legacy
            // session attribute for compatibility with older entry points.
            String client = null;
            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    if (MobileAuthInitController.CLIENT_COOKIE_NAME.equals(c.getName())
                            && c.getValue() != null && !c.getValue().isBlank()) {
                        client = c.getValue();
                        break;
                    }
                }
            }
            if (client == null && request.getSession(false) != null) {
                client = (String) request.getSession(false).getAttribute("oauth2_client");
            }

            if (client != null && mobileRedirects.containsKey(client)) {
                // Mobile flow: redirect so the app can capture the token. Support both
                // plain custom-scheme URIs (e.g. "comprasia://auth") and Android
                // `intent://...#Intent;…;end` URIs — for the latter, query params must
                // be inserted BEFORE the `#Intent;…` block.
                String baseUri = mobileRedirects.get(client);
                String query =
                        "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                                + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                                + "&name="  + URLEncoder.encode(name, StandardCharsets.UTF_8);
                int intentHash = baseUri.indexOf("#Intent;");
                String redirectUrl;
                if (intentHash >= 0) {
                    String before = baseUri.substring(0, intentHash);
                    String intentBlock = baseUri.substring(intentHash);
                    String sep = before.contains("?") ? "&" : "?";
                    redirectUrl = before + sep + query + intentBlock;
                } else {
                    String sep = baseUri.contains("?") ? "&" : "?";
                    redirectUrl = baseUri + sep + query;
                }
                logger.debug("Mobile redirect → {}", baseUri);
                // Clear the mobile-client cookie after use.
                Cookie clear = new Cookie(MobileAuthInitController.CLIENT_COOKIE_NAME, "");
                clear.setPath("/");
                clear.setMaxAge(0);
                clear.setSecure(true);
                clear.setHttpOnly(true);
                response.addCookie(clear);
                // Invalidate the OAuth2 session — mobile clients hold their own JWT.
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                response.sendRedirect(redirectUrl);
            } else {
                // Web flow: set HttpOnly cookie, redirect to dashboard
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                response.addCookie(jwtConfig.createAuthCookie(token));
                response.sendRedirect(frontendOrigin + "/dashboard.html");
            }
        };
    }
}
