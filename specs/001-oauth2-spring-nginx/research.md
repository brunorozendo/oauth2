# Research & Decisions: OAuth2 Implementation

## Decisions

### 1. Framework Version: Spring Boot 4.0.x
- **Decision**: Use Spring Boot 4.0.x and Java 21 as mandated by the Constitution.
- **Rationale**: User explicitly updated `build.gradle` and `constitution.md` to require this version. Ensures modern baseline.

### 2. Architecture: Manual PKCE Controller
- **Decision**: Implement manual `OAuth2Controller` instead of relying on Spring Security's `oauth2Login()` auto-configuration.
- **Rationale**: `oauth2.md` specifies a stateless, backend-for-frontend pattern where the backend manages the PKCE flow manually and issues a simple `JSESSIONID` cookie. This provides finer control over the redirect flow and session creation.
- **Implementation**:
    - Custom `PKCEUtil` for code verifier/challenge.
    - Custom `PKCESessionStore` (ConcurrentHashMap) for temporary state.
    - Explicit `RestTemplate` calls to Google.
    - **Dependency**: `spring-session-core` (generic), `spring-boot-starter-web`.

### 3. Session Management
- **Decision**: Server-side session with `JSESSIONID` cookie (HttpOnly).
- **Implementation**: `ConcurrentHashMap` in memory for MVP. Keyed by `Session ID`. Use `HttpSession` abstraction.

### 4. Frontend Architecture
- **Decision**: Static HTML/JS served by Nginx.
- **Communication**: `fetch` API for all backend interaction.

## Key Unknowns Resolved

- **JWT Validation**: validation logic will be manual or use `spring-security-oauth2-jose` as suggested.
- **Google Auth**: Configured via `.env` (Client ID/Secret). Verified against `client_secret_*.json`:
    - **Redirect URI**: `https://backend.brunorozendo.dev/auth/callback` matches spec.
    - **Origin**: `https://frontend.brunorozendo.dev` matches spec.

### 5. Network Topology (Cloudflare Tunnel)
- **Infrastructure**: Cloudflare Tunnel exposing local ports to public HTTPS domains.
- **Mappings**:
- **Mappings**:
    - `https://backend.brunorozendo.dev` (Backend) - **ALWAYS USE THIS**, never `localhost:9103`.
    - `https://frontend.brunorozendo.dev` (Frontend) - **ALWAYS USE THIS**, never `localhost:8631`.
- **Implications**:
    - **CORS**: Backend MUST allow origin `https://frontend.brunorozendo.dev`.
    - **Cookies**: Should use `Domain=.brunorozendo.dev` to allow sharing (if needed) or rely on standard cross-origin credentials configuration. Given they are subdomains, setting the cookie on the parent domain or specific backend domain with `SameSite=None` (if cross-site) or `SameSite=Lax` (if navigation) is key. We will stick to `SameSite=None; Secure` to support the cross-subdomain fetch calls if not sharing the parent domain cookie, OR effectively treat them as same-site by setting `Domain=.brunorozendo.dev`.
    - **Decision**: Set Cookie `Domain=.brunorozendo.dev` and `Path=/` to allow simple "Lax" usage across subdomains, or explicit `SameSite=None` if treating as distinct origins. **Chosen**: `SameSite=None; Secure` for maximum compatibility with the separate hostnames during the MVP/PoC phase.
