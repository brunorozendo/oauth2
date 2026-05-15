package com.brunorozendo.oauth2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for short-lived OAuth 2.1 PKCE authorization codes.
 *
 * <p>After Google completes its OAuth dance and Spring's OAuth2 success
 * handler fires, we don't redirect the access token directly to the client
 * anymore. Instead we mint a random opaque {@code code}, stash the user
 * claims + PKCE {@code code_challenge} here for ~60 seconds, and redirect
 * the client with {@code ?code=…}. The client then POSTs that code along
 * with its {@code code_verifier} to {@code /api/auth/exchange}; we look it
 * up, validate the verifier against the stored challenge, mint and return
 * the {@code access_token} + {@code refresh_token}, and burn the code.
 *
 * <p>Single-instance only — this is a {@code ConcurrentHashMap}, not Redis.
 * Acceptable for the broker today; if we ever scale to multiple instances
 * we'd swap this for a persistent store.
 */
@Component
public class AuthCodeStore {

    private static final Logger logger = LoggerFactory.getLogger(AuthCodeStore.class);

    /** Codes live for one minute — long enough for the redirect round-trip
     *  plus a slow network, short enough that a leaked code is mostly
     *  worthless. */
    public static final Duration CODE_TTL = Duration.ofSeconds(60);

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService janitor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auth-code-store-gc");
                t.setDaemon(true);
                return t;
            });

    public AuthCodeStore() {
        janitor.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
    }

    public String issue(Map<String, Object> userClaims,
                        String codeChallenge,
                        String codeChallengeMethod,
                        String clientId,
                        String redirectUri) {
        String code = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        map.put(code, new Entry(
                code,
                Map.copyOf(userClaims),
                codeChallenge,
                codeChallengeMethod,
                clientId,
                redirectUri,
                Instant.now().plus(CODE_TTL)));
        return code;
    }

    /** Removes + returns the entry if present and unexpired. */
    public Entry consume(String code) {
        if (code == null) return null;
        Entry e = map.remove(code);
        if (e == null) return null;
        if (Instant.now().isAfter(e.expiresAt())) {
            logger.debug("auth code consumed after expiry: {}", code);
            return null;
        }
        return e;
    }

    private void sweep() {
        Instant now = Instant.now();
        int removed = 0;
        for (var it = map.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now.isAfter(entry.getValue().expiresAt())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) logger.debug("auth-code-store: swept {} expired entries", removed);
    }

    public record Entry(
            String code,
            Map<String, Object> userClaims,
            String codeChallenge,
            String codeChallengeMethod,
            String clientId,
            String redirectUri,
            Instant expiresAt) {}
}
