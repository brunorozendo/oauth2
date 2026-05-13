# Data Model: Google OAuth2 Login

**Feature**: 001-google-oauth2-login
**Date**: 2026-02-17 (Updated: 2026-02-17)
**Storage**: In-memory HashMap (no database, no persistence between restarts)

## Overview

This feature uses **Spring Security's built-in OAuth2 data structures** with minimal custom code. Spring handles PKCE parameters, token storage, and user session data automatically. **No custom entity classes needed** for core OAuth2 flow - we only define DTOs for REST API responses.

**Design Philosophy**: Leverage Spring framework classes instead of creating custom entities.

## Spring-Managed Entities (No Custom Code)

### 1. PKCE Parameters (Managed by Spring Security)

**Purpose**: OAuth2 PKCE flow state (code_verifier, state, nonce)

**Lifecycle**: Spring Security automatically manages this during OAuth2 authorization flow

**Storage**: Spring Security stores PKCE parameters internally (not exposed to application code)

**How Spring handles it**:
```
User clicks login → Spring generates:
  - code_verifier (random 43-128 chars)
  - code_challenge = SHA256(code_verifier)
  - state (CSRF token)
  - nonce (ID token validation)

Google callback → Spring validates:
  - state matches
  - exchanges code + code_verifier for tokens
  - validates nonce in ID token

Spring cleans up automatically after callback
```

**Custom code needed**: **ZERO** - Spring Security handles entire PKCE lifecycle

**Java Representation**: Not needed - Spring uses internal classes

---

### 2. OAuth2User (Spring Security's User Principal)

**Purpose**: Authenticated user information - **Spring provides this automatically**

**Lifecycle**: Spring Security creates after successful login, stores in SecurityContext

**Storage**: `SecurityContext` principal (managed by Spring Security)

**Spring's OAuth2User interface**:
```java
// Spring's built-in interface - NO CUSTOM CLASS NEEDED
public interface OAuth2User extends OAuth2AuthenticatedPrincipal {
    Map<String, Object> getAttributes();  // Contains Google userinfo
    Collection<? extends GrantedAuthority> getAuthorities();
    String getName();  // Primary attribute (defaults to "sub")
}
```

**Accessing user data in controllers**:
```java
@GetMapping("/api/auth/user")
public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal OAuth2User oauth2User) {
    if (oauth2User == null) {
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    Map<String, Object> attributes = oauth2User.getAttributes();
    return ResponseEntity.ok(Map.of(
        "sub", attributes.get("sub"),
        "email", attributes.get("email"),
        "name", attributes.getOrDefault("name", "Unknown User"),
        "picture", attributes.getOrDefault("picture", "/assets/default-avatar.png"),
        "emailVerified", attributes.getOrDefault("email_verified", false)
    ));
}
```

**Custom code needed**: **ZERO** - use Spring's `OAuth2User` directly via `@AuthenticationPrincipal`

---

### 3. OAuth2AuthorizedClient (Spring's Token Container)

**Purpose**: OAuth2 tokens and client metadata - **Spring provides this automatically**

**Lifecycle**: Spring creates after token exchange, automatically refreshes on expiration

**Storage**: `OAuth2AuthorizedClientRepository` (in-memory via HttpSession by default)

**Spring's OAuth2AuthorizedClient class**:
```java
// Spring's built-in class - NO CUSTOM CLASS NEEDED
public class OAuth2AuthorizedClient {
    private ClientRegistration clientRegistration;
    private String principalName;
    private OAuth2AccessToken accessToken;
    private OAuth2RefreshToken refreshToken;
}
```

**Accessing tokens in controllers**:
```java
@GetMapping("/api/auth/user")
public ResponseEntity<?> getCurrentUser(
        @AuthenticationPrincipal OAuth2User oauth2User,
        @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

    Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();

    return ResponseEntity.ok(Map.of(
        "sub", oauth2User.getAttribute("sub"),
        "email", oauth2User.getAttribute("email"),
        "expiresAt", expiresAt.toEpochMilli()
    ));
}
```

**Token refresh** (automatic):
```java
// Spring's OAuth2AuthorizedClientManager automatically refreshes tokens
// when authorizedClient.getAccessToken() is expired
// NO MANUAL REFRESH CODE NEEDED
```

**Custom code needed**: **ZERO** - Spring handles token storage, refresh, and expiration

---

## DTOs (Data Transfer Objects)

### AuthorizationUrlResponse

**Purpose**: Response from `/api/auth/login/google`

**Direction**: Backend → Frontend

**Fields**:
```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "random-state-value"
}
```

**Usage**: Frontend redirects user to `authorizationUrl`

---

### UserInfoResponse

**Purpose**: Response from `/api/auth/user`

**Direction**: Backend → Frontend

**Fields**:
```json
{
  "sub": "google-user-id",
  "email": "user@example.com",
  "name": "User Name",
  "picture": "https://...",
  "emailVerified": true,
  "expiresAt": 1709136000000
}
```

**Usage**: Frontend displays user profile and starts token refresh countdown

**Validation**:
- `expiresAt` is Unix timestamp in milliseconds
- All fields required (use defaults from `UserProfile` if missing)

---

### ErrorResponse

**Purpose**: Standard error response for all endpoints

**Direction**: Backend → Frontend

**Fields**:
```json
{
  "error": "error_code",
  "message": "Human-readable error message",
  "tryAgainUrl": "https://frontend.brunorozendo.dev/?retry=true"
}
```

**Error Codes**:
- `session_expired`: Session timeout or invalid session
- `invalid_state`: CSRF validation failed
- `token_exchange_failed`: Google token endpoint error
- `token_validation_failed`: ID token validation error
- `refresh_failed`: Token refresh error
- `google_unavailable`: Google service unavailable

---

## Session Storage Implementation (Spring-Managed)

**No Database**: All session data stored in-memory using **Spring Security's automatic session management**

**Storage Mechanism**: Spring Security stores OAuth2 data in HttpSession (Tomcat's ConcurrentHashMap)

**What Spring automatically stores in HttpSession**:
1. **`SPRING_SECURITY_CONTEXT`**: Contains `OAuth2AuthenticationToken` with `OAuth2User` principal
2. **`AUTHORIZED_CLIENT`**: Contains `OAuth2AuthorizedClient` with access token, refresh token, expiration
3. **PKCE state**: Managed internally by Spring Security (not visible to application)

**Application code** (minimal):
```java
// Get authenticated user - Spring provides OAuth2User automatically
@GetMapping("/api/auth/user")
public ResponseEntity<?> getCurrentUser(
        @AuthenticationPrincipal OAuth2User oauth2User) {
    // oauth2User is automatically injected from SecurityContext in HttpSession
    return ResponseEntity.ok(oauth2User.getAttributes());
}

// Get tokens - Spring provides OAuth2AuthorizedClient automatically
@GetMapping("/api/auth/token-info")
public ResponseEntity<?> getTokenInfo(
        @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client) {
    // client is automatically injected from OAuth2AuthorizedClientRepository
    Instant expiresAt = client.getAccessToken().getExpiresAt();
    return ResponseEntity.ok(Map.of("expiresAt", expiresAt.toEpochMilli()));
}

// Logout - Spring clears session
@PostMapping("/api/auth/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
    request.getSession().invalidate();  // Spring clears all OAuth2 data
    return ResponseEntity.ok(Map.of("message", "Logged out"));
}
```

**Configuration** (Spring auto-configuration):
```yaml
server:
  servlet:
    session:
      timeout: 60s  # Session timeout
      cookie:
        http-only: true
        secure: true
        same-site: lax

# Spring Security auto-configures:
# - OAuth2AuthenticationToken storage in SecurityContext
# - OAuth2AuthorizedClient storage in HttpSession
# - Automatic token refresh when expired
```

**Lifecycle** (managed by Spring):
- **Creation**: Spring creates session on successful OAuth2 login
- **Storage**: Spring stores OAuth2User + tokens in HttpSession
- **Refresh**: Spring automatically refreshes tokens when expired
- **Expiration**: Sessions expire after 60 seconds of inactivity
- **Cleanup**: Tomcat removes expired sessions automatically
- **Restart**: All session data lost (acceptable for MVP)

**Custom code needed**: **ZERO session management code** - Spring handles everything

---

## Data Flow Diagram (Spring-Managed)

```
1. Frontend: User clicks "Sign in with Google"
   Frontend redirects to: /oauth2/authorization/google
   ↓
   Spring Security automatically:
   ├─> Generates PKCE code_verifier and code_challenge (S256)
   ├─> Generates state (CSRF) and nonce
   ├─> Stores PKCE params internally
   └─> Redirects to Google with code_challenge, state, nonce

2. User grants consent at Google
   Google redirects to: /login/oauth2/code/google?code=...&state=...
   ↓
   Spring Security automatically:
   ├─> Validates state parameter
   ├─> Exchanges code + code_verifier for tokens
   ├─> Validates ID token (signature, issuer, aud, exp, nonce)
   ├─> Fetches user info from Google
   ├─> Creates OAuth2AuthenticationToken with OAuth2User principal
   ├─> Stores in SecurityContext
   ├─> Stores OAuth2AuthorizedClient (tokens) in HttpSession
   └─> Redirects to frontend dashboard (default: /)

3. /api/auth/user (custom endpoint)
   Spring automatically injects:
   ├─> @AuthenticationPrincipal OAuth2User (from SecurityContext)
   ├─> @RegisteredOAuth2AuthorizedClient (from HttpSession)
   └─> Controller returns: UserInfoResponse with expiresAt

4. /api/auth/refresh (optional custom endpoint)
   Spring automatically:
   ├─> Injects OAuth2AuthorizedClient
   ├─> Detects if access token expired
   ├─> Uses refresh_token to get new access_token from Google
   ├─> Updates OAuth2AuthorizedClient in HttpSession
   └─> Returns new expiresAt

5. /api/auth/logout (custom endpoint)
   Controller code:
   ├─> Calls request.getSession().invalidate()
   └─> Spring clears all OAuth2 data from session
   └─> Redirects to frontend login

ZERO custom entity classes needed - Spring provides everything:
- OAuth2User (user principal)
- OAuth2AuthorizedClient (tokens)
- OAuth2AuthenticationToken (SecurityContext)
```

---

## Validation Summary

| Entity | Creation | Validation | Expiration |
|--------|----------|------------|------------|
| PKCESession | `/api/auth/login/google` | `isExpired()` check, state comparison | 10 minutes or callback |
| UserProfile | `/api/auth/callback` | Email non-empty, defaults for optional fields | Session timeout or logout |
| TokenSet | `/api/auth/callback` | ID token signature/claims, expiresAt future | Session timeout or logout |

**Key constraints**:
- All entities are `Serializable` (required for HttpSession storage)
- All timestamps use `Instant` (UTC, unambiguous)
- All validation errors result in redirect to login with error message
- No database persistence - data lost on backend restart (acceptable for MVP)
