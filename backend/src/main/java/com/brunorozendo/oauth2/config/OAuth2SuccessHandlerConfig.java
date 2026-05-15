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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class OAuth2SuccessHandlerConfig {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandlerConfig.class);

    @Bean
    public AuthenticationSuccessHandler oAuth2SuccessHandler(
            JwtConfig jwtConfig,
            AuthCodeStore codes,
            @Value("${app.frontend.origin}") String frontendOrigin,
            @Value("${app.mobile.redirect-uris.inventmove:}") String inventmoveUri,
            @Value("${app.mobile.redirect-uris.bossbill:}") String bossbillUri,
            @Value("${app.mobile.redirect-uris.anotadissimo:}") String anotadissimoUri,
            @Value("${app.mobile.redirect-uris.anotadissimo-ios:}") String anotadissimoIosUri,
            @Value("${app.mobile.redirect-uris.anotadissimo-web:}") String anotadissimoWebUri,
            @Value("${app.mobile.redirect-uris.anotadissimo-ext:}") String anotadissimoExtUri) {

        Map<String, String> mobileRedirects = new HashMap<>();
        if (!inventmoveUri.isBlank()) mobileRedirects.put("inventmove", inventmoveUri);
        if (!bossbillUri.isBlank()) mobileRedirects.put("bossbill", bossbillUri);
        if (!anotadissimoUri.isBlank()) mobileRedirects.put("anotadissimo", anotadissimoUri);
        if (!anotadissimoIosUri.isBlank()) mobileRedirects.put("anotadissimo-ios", anotadissimoIosUri);
        if (!anotadissimoWebUri.isBlank()) mobileRedirects.put("anotadissimo-web", anotadissimoWebUri);
        if (!anotadissimoExtUri.isBlank()) mobileRedirects.put("anotadissimo-ext", anotadissimoExtUri);

        return (HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) -> {

            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oauth2User.getAttributes();

            String email   = (String) attributes.getOrDefault("email",          "unknown@example.com");
            String name    = (String) attributes.getOrDefault("name",            "Unknown User");
            String picture = (String) attributes.getOrDefault("picture",         "/assets/default-avatar.png");
            Object sub     = attributes.getOrDefault("sub",                      "unknown");
            Object emailOk = attributes.getOrDefault("email_verified",           false);

            Map<String, Object> userClaims = new LinkedHashMap<>();
            userClaims.put("sub",           sub);
            userClaims.put("email",         email);
            userClaims.put("name",          name);
            userClaims.put("picture",       picture);
            userClaims.put("emailVerified", emailOk);

            // Read all three init cookies up front so we know which client
            // started the flow and whether they sent PKCE parameters.
            String client = null;
            String codeChallenge = null;
            String codeChallengeMethod = null;
            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    String v = c.getValue();
                    if (v == null || v.isBlank()) continue;
                    if (MobileAuthInitController.CLIENT_COOKIE_NAME.equals(c.getName())) {
                        client = v;
                    } else if (MobileAuthInitController.CHALLENGE_COOKIE_NAME.equals(c.getName())) {
                        codeChallenge = v;
                    } else if (MobileAuthInitController.CHALLENGE_METHOD_COOKIE_NAME.equals(c.getName())) {
                        codeChallengeMethod = v;
                    }
                }
            }
            if (client == null && request.getSession(false) != null) {
                client = (String) request.getSession(false).getAttribute("oauth2_client");
            }

            // Clear all auth-flow cookies on the way out.
            clearCookie(response, MobileAuthInitController.CLIENT_COOKIE_NAME);
            clearCookie(response, MobileAuthInitController.CHALLENGE_COOKIE_NAME);
            clearCookie(response, MobileAuthInitController.CHALLENGE_METHOD_COOKIE_NAME);
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }

            // Resolve target URI for mobile / extension / web clients.
            String redirectUri = (client != null) ? mobileRedirects.get(client) : null;

            if (redirectUri != null) {
                String query;
                if (codeChallenge != null) {
                    // PKCE path: issue an opaque code, the client redeems it
                    // at /api/auth/exchange.
                    String code = codes.issue(userClaims, codeChallenge, codeChallengeMethod,
                            client, redirectUri);
                    query = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
                    logger.debug("PKCE redirect → {} (code issued for {})", client, email);
                } else {
                    // Legacy fallback: mint access + refresh and stamp into
                    // the redirect URI directly. Phased migration path so
                    // older clients still work pre-update.
                    String accessToken = jwtConfig.generateAccessToken(userClaims);
                    String refreshToken = jwtConfig.generateRefreshToken(userClaims);
                    query =
                            "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                                    + "&refresh=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                                    + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                                    + "&name="  + URLEncoder.encode(name, StandardCharsets.UTF_8);
                    logger.debug("legacy redirect → {} (token+refresh for {})", client, email);
                }
                response.sendRedirect(appendQuery(redirectUri, query));
                return;
            }

            // No mobile client — fall through to the legacy web cookie flow.
            String accessToken = jwtConfig.generateAccessToken(userClaims);
            response.addCookie(jwtConfig.createAuthCookie(accessToken));
            response.sendRedirect(frontendOrigin + "/dashboard.html");
        };
    }

    /** Mobile flows accept both plain custom-scheme URIs (e.g.
     *  {@code anotadissimo://auth}) and Android intent URIs
     *  ({@code intent://auth#Intent;…;end}). The Intent block has to stay
     *  last, so insert query params before {@code #Intent;}. */
    private static String appendQuery(String baseUri, String query) {
        int intentHash = baseUri.indexOf("#Intent;");
        if (intentHash >= 0) {
            String before = baseUri.substring(0, intentHash);
            String intentBlock = baseUri.substring(intentHash);
            String sep = before.contains("?") ? "&" : "?";
            return before + sep + query + intentBlock;
        }
        String sep = baseUri.contains("?") ? "&" : "?";
        return baseUri + sep + query;
    }

    private static void clearCookie(HttpServletResponse response, String name) {
        Cookie clear = new Cookie(name, "");
        clear.setPath("/");
        clear.setMaxAge(0);
        clear.setSecure(true);
        clear.setHttpOnly(true);
        clear.setAttribute("SameSite", "Lax");
        response.addCookie(clear);
    }
}
