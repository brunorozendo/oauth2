package com.brunorozendo.oauth2.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.regex.Pattern;

/**
 * Entry point used by mobile clients. Sets a dedicated cookie that records which
 * mobile client started the OAuth flow, then forwards to the standard Spring
 * /oauth2/authorization/google endpoint.
 *
 * <p>The cookie path is the safer transport than the OAuth2 session attribute we
 * used previously: when a user is already authenticated (e.g. via the
 * AUTH_TOKEN cookie left by a prior web login), Spring's OAuth flow on the
 * subsequent request does not always reliably carry session attributes through
 * to the SuccessHandler. The cookie travels with every request to the backend,
 * so the SuccessHandler can always read it.
 */
@RestController
@RequestMapping("/api/auth/mobile")
public class MobileAuthInitController {

    private static final Logger logger = LoggerFactory.getLogger(MobileAuthInitController.class);
    private static final Pattern VALID_CLIENT = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");
    public static final String CLIENT_COOKIE_NAME = "oauth2_mobile_client";
    private static final int CLIENT_COOKIE_MAX_AGE_SECONDS = 600;

    @GetMapping("/{client}/google")
    public RedirectView startMobileGoogleFlow(
            @PathVariable("client") String client,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (client == null || !VALID_CLIENT.matcher(client).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "client must match " + VALID_CLIENT.pattern());
        }

        Cookie cookie = new Cookie(CLIENT_COOKIE_NAME, client);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(CLIENT_COOKIE_MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        logger.debug("Mobile client cookie set for: {}", client);

        // Preserve the existing ?client=… session capture too so a single
        // change rolls out gracefully alongside older app builds.
        return new RedirectView("/oauth2/authorization/google?client=" + client, false);
    }
}
