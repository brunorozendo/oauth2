# Feature Specification: Google OAuth2 Login with Full Authentication Flow

**Feature Branch**: `001-google-oauth2-login`
**Created**: 2026-02-16
**Status**: Draft
**Input**: User description: "OAuth2 Login with Google — Full Authentication Flow

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
- Browser caching must not serve stale frontend code after deployments."

## Clarifications

### Session 2026-02-16

- Q: How long should an authenticated backend session remain valid before requiring re-authentication? → A: 1 minute for MVP validation; configurable via property (session timeout in seconds); after 1 minute, request token refresh
- Q: What cache control strategy should be used to prevent stale frontend code? → A: No caching (Cache-Control: no-store) - forces fresh fetch every time
- Q: Should the system automatically refresh the access token when it's about to expire? → A: Automatic refresh when token expires; "Refresh Now" button provides manual alternative for users
- Q: Should there be rate limiting on login attempts to prevent abuse? → A: No rate limiting for MVP; focused on functionality, security hardening deferred to production
- Q: What level of observability (logging, monitoring) is needed for this MVP? → A: Development-friendly logging - console logs for frontend, structured server logs for auth flow, errors, and token operations

## User Scenarios & Testing

### User Story 1 - Google OAuth2 Login Flow (Priority: P1)

A visitor arrives at the application and needs to authenticate using their Google account to access protected features. They click "Sign in with Google", are redirected to Google's consent screen, grant permissions, and are redirected back to the application dashboard with an active authenticated session.

**Why this priority**: This is the foundational authentication mechanism. Without it, no other features can function. This delivers immediate value by allowing users to securely access the application.

**Independent Test**: Can be fully tested by clicking "Sign in with Google", completing Google's consent flow, and verifying successful redirection to the dashboard with an authenticated session. Success is measured by the presence of user profile information in the dashboard.

**Acceptance Scenarios**:

1. **Given** an unauthenticated user on the login page, **When** they click "Sign in with Google", **Then** they are redirected to Google's OAuth2 authorization page with correct PKCE parameters
2. **Given** a user on Google's consent screen, **When** they grant permissions and Google redirects back to the application, **Then** the backend exchanges the authorization code for tokens, validates the ID token, and creates an authenticated session
3. **Given** a successful authentication, **When** the user is redirected to the dashboard, **Then** they see their Google profile information (name, email, avatar)
4. **Given** a user fails to grant consent on Google's page, **When** Google redirects back with an error, **Then** the user sees a clear error message with a "Try Again" link
5. **Given** the token exchange fails for any reason, **When** the backend receives an error from Google, **Then** the user sees a clear error message explaining the issue with a "Try Again" link

---

### User Story 2 - Post-Login Dashboard with Token Lifecycle Management (Priority: P2)

Once authenticated, users see their Google profile information and can monitor their session's token expiration. The dashboard displays a live countdown timer showing when the current access token expires, giving users visibility into their session state and control over token refresh.

**Why this priority**: Provides essential post-authentication user experience and transparency about session state. This builds user trust and allows them to manage their authenticated session effectively.

**Independent Test**: Can be fully tested by authenticating a user, observing the dashboard display of profile information and token expiration countdown, and verifying that the timer accurately reflects the remaining token lifetime. Delivers value by giving users session awareness.

**Acceptance Scenarios**:

1. **Given** an authenticated user lands on the dashboard, **When** the dashboard loads, **Then** it displays the user's name, email, and avatar from their Google profile
2. **Given** an authenticated user on the dashboard, **When** the page loads, **Then** a live countdown timer displays the remaining time until the access token expires
3. **Given** a user's profile is missing optional fields (e.g., avatar), **When** the dashboard renders, **Then** it displays sensible defaults ("Unknown User" for missing name, placeholder avatar image) and logs debug information to the console
4. **Given** an authenticated user on the dashboard, **When** they view the page, **Then** they see both a "Refresh Token" button and a "Logout" button
5. **Given** the user info endpoint is called, **When** it returns data, **Then** it includes both profile data (name, email, avatar) and token expiration timestamp in a single response

---

### User Story 3 - Automatic and Manual Token Refresh (Priority: P2)

The system automatically refreshes access tokens when they expire to maintain seamless sessions. Users also have a "Refresh Now" button to manually refresh tokens on demand without waiting for automatic refresh.

**Why this priority**: Critical for user experience by preventing session interruptions. Automatic refresh ensures continuous access while manual option gives users control. Elevated to P2 due to direct impact on session continuity.

**Independent Test**: Can be fully tested by authenticating a user, waiting for token to expire and observing automatic refresh, or clicking "Refresh Now" to manually trigger refresh. Countdown timer should reset in both cases. Delivers value by providing uninterrupted session extension.

**Acceptance Scenarios**:

1. **Given** an authenticated user's token has expired, **When** the system detects expiration, **Then** it automatically refreshes the token without user intervention
2. **Given** an automatic token refresh succeeds, **When** the new token is obtained, **Then** the countdown timer resets to show the new expiration time
3. **Given** an authenticated user on the dashboard, **When** they click "Refresh Now" button, **Then** the system obtains a new access token from Google without requiring re-authentication
4. **Given** a manual token refresh succeeds, **When** the new token is obtained, **Then** the countdown timer resets to show the new expiration time
5. **Given** a token refresh fails (automatic or manual), **When** the refresh attempt returns an error, **Then** the user is redirected to the login page with an explanatory message
6. **Given** a token refresh is in progress (automatic or manual), **When** the user is waiting, **Then** the UI provides visual feedback (e.g., button disabled, loading indicator)

---

### User Story 4 - Logout and Session Termination (Priority: P3)

Users can explicitly end their authenticated session by clicking a logout button. This cleanly terminates their session both in the application and at Google, then redirects them to the login page.

**Why this priority**: Provides users with control over their session and ensures security by properly terminating authentication state. Essential for security but not blocking for core authentication flow.

**Independent Test**: Can be fully tested by authenticating a user, clicking "Logout", and verifying the session is invalidated and the user is redirected to the login page. Attempting to access protected resources after logout should fail. Delivers value by ensuring secure session termination.

**Acceptance Scenarios**:

1. **Given** an authenticated user on the dashboard, **When** they click "Logout", **Then** the backend invalidates the server-side session
2. **Given** the logout process is initiated, **When** the backend processes it, **Then** tokens are revoked at Google
3. **Given** the session is terminated, **When** the logout completes, **Then** the user is redirected to the login page
4. **Given** a logged-out user, **When** they attempt to access the dashboard or user info endpoint, **Then** they are redirected to the login page
5. **Given** a token revocation fails at Google, **When** the logout process continues, **Then** the local session is still invalidated and the user is redirected to login

---

### User Story 5 - Automatic Session Expiry Handling (Priority: P2)

When a user's session expires or becomes invalid, any attempt to access protected pages automatically redirects them to the login page. This prevents stale or invalid dashboard states from being displayed.

**Why this priority**: Critical for security and data integrity. Prevents users from seeing stale data or attempting actions with invalid sessions. This is prioritized as P2 because it's essential for security but builds on the core login flow.

**Independent Test**: Can be fully tested by allowing a session to expire (or manually invalidating it), then attempting to load the dashboard or make API calls. The system should redirect to login without showing stale content. Delivers value by ensuring security and preventing confusing user experiences.

**Acceptance Scenarios**:

1. **Given** a user's session has expired, **When** they attempt to load the dashboard, **Then** they are immediately redirected to the login page
2. **Given** a user with an invalid session, **When** they make an API call to the user info endpoint, **Then** the backend returns an unauthorized status and the frontend redirects to login
3. **Given** an expired session, **When** the redirect to login occurs, **Then** no stale dashboard data is displayed to the user
4. **Given** a session expires while a user is viewing the dashboard, **When** they attempt to refresh the token or perform any action, **Then** they are redirected to login with an explanatory message

---

### Edge Cases

- **What happens when Google's authorization service is unavailable?** User sees an error message indicating the service is temporarily unavailable with a "Try Again" option.
- **What happens when a user denies consent on Google's page?** User is redirected back with a clear error message explaining consent was denied and offering to try again.
- **What happens when the backend receives a mismatched state parameter (CSRF attack)?** Request is rejected, session is terminated, and user sees a security error message requiring re-login.
- **What happens when token exchange fails due to network issues?** User sees an error message about connectivity problems with a "Try Again" option.
- **What happens when a user's Google account provides incomplete profile data?** Dashboard displays reasonable defaults ("Unknown User", placeholder avatar) and logs debug information to console for troubleshooting.
- **What happens when frontend code is updated but browser attempts to serve cached version?** Cache-Control: no-store headers prevent browser caching, forcing fresh fetch of all static assets on every load.
- **What happens when token refresh fails because user revoked app permissions at Google?** User is redirected to login page with a message explaining they need to re-authorize the application.
- **What happens when the countdown timer reaches zero?** System automatically refreshes the access token; if automatic refresh succeeds, countdown resets with new expiration time; if it fails, user is redirected to login page.
- **What happens when a user doesn't interact during session timeout?** Backend session is invalidated after timeout period (60 seconds for MVP) and any subsequent request redirects user to login page with a message explaining the session expired.
- **What happens when automatic token refresh is in progress?** User sees visual feedback (loading indicator), "Refresh Now" button is disabled, and any user-initiated actions wait for refresh to complete.

## Requirements

### Functional Requirements

- **FR-001**: System MUST provide a "Sign in with Google" button that initiates the OAuth2 authorization flow
- **FR-002**: System MUST implement OAuth2 Authorization Code flow with PKCE (S256) when initiating login
- **FR-003**: Backend MUST generate and securely store code_verifier, code_challenge, state, and nonce in server-side session before redirecting to Google
- **FR-004**: System MUST redirect users to Google's OAuth2 authorization endpoint with correct parameters (client_id, redirect_uri, scope, state, nonce, code_challenge)
- **FR-005**: Backend MUST validate the state parameter on callback to prevent CSRF attacks
- **FR-006**: Backend MUST exchange the authorization code for tokens using the stored code_verifier
- **FR-007**: Backend MUST validate the ID token (JWT signature, issuer, audience, expiration, nonce)
- **FR-008**: Backend MUST fetch user profile information from Google's userinfo endpoint
- **FR-009**: Backend MUST create an authenticated server-side session after successful token validation
- **FR-010**: System MUST redirect authenticated users to the dashboard after successful login
- **FR-011**: Dashboard MUST display user's name, email, and avatar from Google profile
- **FR-012**: Dashboard MUST display a live countdown timer showing time until access token expiration
- **FR-013**: User info endpoint MUST return both profile data and token expiration timestamp in a single response
- **FR-014**: Dashboard MUST display sensible defaults for missing profile fields and log debug information to browser console
- **FR-015**: Dashboard MUST provide a "Refresh Now" button for manual token refresh
- **FR-016**: Dashboard MUST provide a "Logout" button for session termination
- **FR-017**: System MUST automatically refresh access tokens when they expire without user intervention
- **FR-018**: System MUST refresh access tokens without re-authentication when user clicks "Refresh Now" button (manual alternative)
- **FR-019**: System MUST update the countdown timer with new expiration time after successful token refresh (automatic or manual)
- **FR-020**: System MUST redirect users to login page with explanatory message if token refresh fails (automatic or manual)
- **FR-021**: System MUST invalidate server-side session when user logs out
- **FR-022**: System MUST attempt to revoke tokens at Google when user logs out (call Google's revoke endpoint); if revocation fails due to network issues or Google unavailability, log the error but continue with local session invalidation
- **FR-023**: System MUST redirect users to login page after successful logout
- **FR-024**: System MUST redirect users to login page if session is expired or invalid when accessing protected pages
- **FR-025**: System MUST display clear error messages with "Try Again" links for all authentication failures
- **FR-026**: Frontend MUST never directly handle or store OAuth2 tokens (all tokens managed by backend)
- **FR-027**: Frontend MUST serve all static assets with Cache-Control: no-store header to prevent browser caching and ensure fresh code after deployments
- **FR-028**: Backend MUST store all authentication state (tokens, PKCE parameters) in server-side session only
- **FR-029**: System MUST request OpenID Connect scopes (openid, email, profile) from Google
- **FR-030**: Backend MUST provide a configurable session timeout property (in seconds) that determines when authenticated sessions expire
- **FR-031**: System MUST provide visual feedback during token refresh operations (both automatic and manual) such as disabled buttons or loading indicators
- **FR-032**: Frontend MUST log authentication flow events to browser console including login initiation, redirects, token refresh attempts, and errors
- **FR-033**: Backend MUST log structured authentication events including PKCE generation, token exchange, validation steps, refresh operations, logout, and all errors
- **FR-034**: Backend MUST log all failed authentication attempts with sufficient context for debugging (error type, timestamp, session ID) without exposing sensitive tokens

### Key Entities

**Note**: These are **conceptual entities** that describe the data model. The implementation uses **Spring Security's built-in classes** rather than custom entity classes per the "brutally simplistic" design philosophy. See [data-model.md](data-model.md) for Spring Security implementation details.

- **User Profile** (implemented via `org.springframework.security.oauth2.core.user.OAuth2User`): Represents the authenticated user with attributes from Google (sub/user ID, email, name, avatar/picture, email verification status)
- **PKCE Session** (handled internally by Spring Security): Temporary server-side session storing OAuth2 flow state (code_verifier, state parameter, nonce, creation timestamp) - Spring manages automatically
- **Authenticated Session** (implemented via `HttpSession` + `SecurityContext`): Server-side session for authenticated users containing user profile data, access token, refresh token, and token expiration timestamp
- **Token Set** (implemented via `org.springframework.security.oauth2.client.OAuth2AuthorizedClient`): Collection of OAuth2 tokens received from Google (access_token, id_token, refresh_token, expires_in, scope)

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can complete the entire login flow from clicking "Sign in with Google" to viewing their dashboard in under 10 seconds (excluding Google's consent page interaction time)
- **SC-002**: 100% of authentication attempts validate PKCE code_verifier and state parameters to prevent CSRF and authorization code interception attacks
- **SC-003**: Dashboard displays user profile information within 2 seconds of authentication completion
- **SC-004**: Token countdown timer updates in real-time with accuracy within 1 second of actual expiration
- **SC-005**: System automatically refreshes access tokens on expiration, or users can manually refresh via "Refresh Now" button, both completing in under 3 seconds without re-authentication
- **SC-006**: 100% of logout operations fully invalidate server-side sessions and revoke tokens at Google
- **SC-007**: Users with expired or invalid sessions are redirected to login within 1 second of attempting to access protected resources
- **SC-008**: Error messages are displayed to users within 2 seconds of any authentication failure occurring
- **SC-009**: Zero authentication tokens are exposed to the frontend or stored in browser storage
- **SC-010**: Frontend serves fresh code immediately after deployment with no browser caching (Cache-Control: no-store enforced on all static assets)
- **SC-011**: All authentication flow events (login, token refresh, logout, errors) are logged with structured format enabling efficient debugging and troubleshooting

## Assumptions

- **Technical Architecture**: Backend is Java Spring Boot, frontend is a static SPA, both on separate subdomains behind a reverse proxy
- **OAuth2 Provider**: Google OAuth2 service is the only authentication provider
- **Session Storage**: Backend uses server-side session storage (in-memory or Redis) for PKCE and user sessions
- **Token Expiration**: Google access tokens expire according to Google's standard policy (typically 1 hour)
- **Session Timeout (MVP)**: Backend session timeout configured to 60 seconds (1 minute) for MVP validation purposes; production value should be longer and configurable via application property
- **Rate Limiting (MVP)**: No rate limiting on login attempts for MVP; focused on functionality validation; security hardening including rate limiting, brute-force protection, and abuse prevention deferred to production
- **Network Reliability**: Standard web application network conditions; transient failures handled with user-friendly error messages
- **Browser Support**: Modern browsers with JavaScript enabled and cookie support
- **HTTPS**: All communication occurs over HTTPS (enforced by reverse proxy)
- **Scope**: Initial implementation focuses on Google authentication only; other providers may be added later

## Dependencies

- **External Service**: Google OAuth2 authorization server (accounts.google.com)
- **External Service**: Google Token endpoint (oauth2.googleapis.com/token)
- **External Service**: Google UserInfo endpoint (googleapis.com/oauth2/v3/userinfo)
- **External Service**: Google's public JWK keys (googleapis.com/oauth2/v3/certs) for ID token validation
- **Infrastructure**: Reverse proxy configuration for routing frontend and backend on separate subdomains
- **Infrastructure**: HTTPS/TLS certificates for secure communication
- **Reference Documentation**: OAuth2.md file in project root containing implementation details and Spring Boot code examples
