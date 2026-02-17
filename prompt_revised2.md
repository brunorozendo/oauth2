# Revised Prompts — Spec-Kit Philosophy

## Step 0: Update Constitution First

Before running any spec-kit command, add these missing guardrails to the constitution
(lessons from previous failed runs). Run `/speckit.constitution` with:

```
/speckit.constitution Add the following lessons-learned as architectural rules:

- **No default oauth2Login():** Do NOT use Spring Security's built-in `oauth2Login()` auto-configuration. The OAuth2 flow must be implemented manually via a custom controller to maintain full control over the callback, token exchange, and session creation.

- **Session Authentication Persistence:** After a successful OAuth2 callback, the `UsernamePasswordAuthenticationToken` must be both set in `SecurityContextHolder` AND explicitly saved to the `HttpSession` under `HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY`. Without both steps, subsequent authenticated requests will fail silently.

- **CORS in SecurityFilterChain:** CORS must be configured directly in the `SecurityFilterChain` via `.cors(cors -> cors.configurationSource(...))`, NOT only in `WebMvcConfigurer`. Spring Security intercepts requests before MVC — a MVC-only CORS config will cause 403 on preflight requests.

- **Reverse Proxy Headers:** Configure `server.forward-headers-strategy: framework` in `application.yml`. Without this, Spring generates `http://` redirect URIs instead of `https://` when behind a TLS-terminating reverse proxy, breaking the OAuth2 callback.

- **ArchUnit Exceptions:** ArchUnit layer rules must allow `org.slf4j` access in the Controller layer. Without this exception, any logging in controllers causes architecture test failures.

- **CSRF Strategy:** CSRF is disabled for `/auth/**` endpoints. The OAuth2 `state` parameter provides equivalent CSRF protection during the authorization flow. Document this tradeoff explicitly in the security config.
```

---

## Step 1: Specify (What, not How)

```
/speckit.specify OAuth2 Login with Google — Full Authentication Flow

Actors:
- Unauthenticated User: A visitor who has not yet logged in.
- Authenticated User: A visitor with an active session.

User Workflows:

1. Login:
   - User visits the frontend and clicks "Sign in with Google".
   - User is redirected to Google's consent screen.
   - After granting consent, the user is redirected back and lands on the Dashboard.
   - If login fails at any point, the user sees a clear error message with a "Try Again" link.

2. Dashboard (post-login):
   - Displays the user's Google profile: name, email, and avatar.
   - Shows a live countdown timer indicating when the current access token expires.
   - The user endpoint returns both the profile data and the token expiration timestamp in a single response so the frontend can drive the countdown.
   - If any profile field is missing, the dashboard displays sensible defaults ("Unknown User", placeholder avatar) and logs debug info to the console.
   - Provides a "Refresh Token" button that obtains a new access token without requiring re-login.
   - Provides a "Logout" button.

3. Token Refresh:
   - User clicks "Refresh Token".
   - The token is refreshed silently; the countdown resets with the new expiration.
   - If refresh fails (e.g., revoked consent), the user is redirected to the login page with an explanatory message.

4. Logout:
   - User clicks "Logout".
   - The server-side session is fully invalidated.
   - The user is redirected to the login page.

5. Session Expiry:
   - If the user's session has expired or is invalid, any page load redirects to login.
   - No stale dashboard state is shown.

Constraints:
- The frontend and backend are on separate subdomains behind a reverse proxy.
- The frontend is a static SPA — no server-side rendering.
- All authentication state lives on the backend; the frontend never handles tokens directly.
- Browser caching must not serve stale frontend code after deployments.

Use my research @oauth2.md
```

---

## Step 2: Clarify (Gate before planning)

```
/speckit.clarify
```

---

## Step 3: Plan (Where technical decisions are referenced)

```
/speckit.plan
- Frontend: https://frontend.brunorozendo.dev (proxied to localhost:8631)
- Backend: https://backend.brunorozendo.dev (proxied to localhost:9103)
- Always use the DNS names; never use host:port directly.

Reference files:
- Environment variables: /Users/bruno/Developer/projects/oauth2/.env
- Google OAuth credentials: /Users/bruno/Developer/projects/oauth2/client_secret_22640646600-eb8jplm4rg3msre29boc1j84m97hmjqk.apps.googleusercontent.com.json
- OAuth2 research notes: /Users/bruno/Developer/projects/oauth2/oauth2.md

All architectural decisions, security guardrails, and technology choices are defined in the constitution. Do not deviate from them.

be brutally simplistic;
Use all the features offered by the framework Spring Boot 

Any kind of SQL should be used only with Flyway

create arcunit tests to validate all architecture;

The criteria of acceptence is: 
`podman compose build; podman compose up` (this command MUST work)
```

---

## Step 4: Tasks (Verify, don't micro-manage)

```
/speckit.tasks
Add a final verification task:
- Run `podman compose build --no-cache && podman compose up -d`
- Confirm the application starts with no errors
- All tests pass (unit, ArchUnit, E2E)

criterias of acceptence:
- run services then run E2E : Login -> Dashboard renders profile -> Countdown ticks -> Refresh Token resets timer -> Logout returns to login
```

---

## Step 5: Analyze (Consistency check before implementation)

```
/speckit.analyze
```

---

## Step 6: Implement

```
/speckit.implement
```
