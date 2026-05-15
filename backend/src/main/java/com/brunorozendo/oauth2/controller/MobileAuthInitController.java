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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.regex.Pattern;

/**
 * Entry point used by mobile / extension / web clients. Drops a few short-
 * lived cookies that the OAuth2 success handler later reads:
 * <ul>
 *   <li>{@code oauth2_mobile_client} — which client initiated (e.g.
 *       {@code anotadissimo-web}). Used to pick the redirect URI.</li>
 *   <li>{@code oauth2_code_challenge} + {@code oauth2_code_challenge_method}
 *       — when the client sent PKCE parameters, the success handler issues
 *       an opaque {@code ?code=…} instead of the legacy {@code ?token=…}.
 *       Missing → legacy direct-token redirect.</li>
 * </ul>
 *
 * <p>Cookies are the safer transport than the OAuth2 session attribute we
 * used previously: when a user is already authenticated (e.g. via the
 * {@code AUTH_TOKEN} cookie left by a prior web login), Spring's OAuth flow
 * on the subsequent request does not always reliably carry session
 * attributes through to the SuccessHandler. The cookies travel with every
 * request to the backend, so the handler can always read them.
 */
@RestController
@RequestMapping("/api/auth/mobile")
public class MobileAuthInitController {

    private static final Logger logger = LoggerFactory.getLogger(MobileAuthInitController.class);
    private static final Pattern VALID_CLIENT = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");
    /** Base64url alphabet, 43–128 chars per RFC 7636 §4.1. */
    private static final Pattern VALID_CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");

    public static final String CLIENT_COOKIE_NAME = "oauth2_mobile_client";
    public static final String CHALLENGE_COOKIE_NAME = "oauth2_code_challenge";
    public static final String CHALLENGE_METHOD_COOKIE_NAME = "oauth2_code_challenge_method";

    private static final int COOKIE_MAX_AGE_SECONDS = 600;

    @GetMapping("/{client}/google")
    public RedirectView startMobileGoogleFlow(
            @PathVariable("client") String client,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (client == null || !VALID_CLIENT.matcher(client).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "client must match " + VALID_CLIENT.pattern());
        }

        setCookie(response, CLIENT_COOKIE_NAME, client);

        if (codeChallenge != null && !codeChallenge.isBlank()) {
            if (!VALID_CHALLENGE.matcher(codeChallenge).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "code_challenge must be 43–128 base64url chars");
            }
            String method = codeChallengeMethod == null || codeChallengeMethod.isBlank()
                    ? "S256" : codeChallengeMethod;
            if (!"S256".equalsIgnoreCase(method)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "only code_challenge_method=S256 is supported");
            }
            setCookie(response, CHALLENGE_COOKIE_NAME, codeChallenge);
            setCookie(response, CHALLENGE_METHOD_COOKIE_NAME, method);
            logger.debug("PKCE init for client={} (challenge len={})", client, codeChallenge.length());
        } else {
            logger.debug("non-PKCE init for client={} (legacy redirect-with-token)", client);
        }

        // Preserve the existing ?client=… session capture too so a single
        // change rolls out gracefully alongside older app builds.
        return new RedirectView("/oauth2/authorization/google?client=" + client, false);
    }

    private static void setCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
