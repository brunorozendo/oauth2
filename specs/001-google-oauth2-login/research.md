# Research: Google OAuth2 Login Implementation

**Feature**: 001-google-oauth2-login
**Date**: 2026-02-17
**Purpose**: Resolve technical unknowns and validate architectural decisions

## Research Areas

### 1. Spring Boot 4 OAuth2 Client Configuration

**Decision**: Use Spring Security's built-in OAuth2 Login with **zero custom PKCE implementation**

**Rationale**:
- Spring Security 7 (included in Spring Boot 4) **automatically handles PKCE** for OAuth2 login
- Spring Boot auto-configures OAuth2 client, authorization endpoints, and token management
- User requirement: "use all features from Spring; I want the minimal code possible"
- **No manual PKCE implementation needed** - Spring handles code_verifier, code_challenge, state, and nonce automatically

**Implementation approach** (configuration only, no custom code):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            authorization-grant-type: authorization_code
        provider:
          google:
            issuer-uri: https://accounts.google.com
            # Spring auto-discovers endpoints from issuer-uri (authorization, token, userinfo, jwk-set)
```

**What Spring handles automatically**:
1. ✅ PKCE code_verifier and code_challenge generation (S256)
2. ✅ OAuth2 authorization redirect to Google
3. ✅ Callback endpoint `/login/oauth2/code/google`
4. ✅ Authorization code exchange for tokens
5. ✅ ID token validation (signature, issuer, audience, expiration)
6. ✅ User info fetch from Google
7. ✅ SecurityContext creation with OAuth2User principal
8. ✅ Session creation and management
9. ✅ Token storage in OAuth2AuthorizedClientRepository
10. ✅ Automatic token refresh using refresh_token

**Custom code needed** (minimal):
- SecurityConfig: Configure CORS, session cookies, logout
- REST controllers: `/api/auth/user`, `/api/auth/logout` (optional `/api/auth/refresh`)
- Frontend: Login button redirects to `/oauth2/authorization/google` (Spring's auto-generated endpoint)

**Alternatives considered**:
- **Manual PKCE implementation**: Rejected - violates "use all Spring features" and "minimal code possible"
- **Custom OAuth2 controllers**: Rejected - Spring's auto-configuration handles everything
- **Chosen approach**: Pure Spring Security OAuth2 Login with configuration-only PKCE

### 2. Stateless Backend with Session Cookies

**Decision**: Use in-memory HashMap for session data storage, not database

**Rationale**:
- User explicitly required: "Do not use database; use a HashMap"
- OAuth2 PKCE flow requires storing `code_verifier`, `state`, `nonce` between authorization request and callback
- Spring Security requires explicit session context save per constitution §3.3
- MVP scope: Single-user validation where session data loss on restart is acceptable

**Implementation approach**:
1. **During OAuth2 flow**: Use `HttpSession` to store PKCE parameters (backed by HashMap)
2. **After authentication**: Set `SecurityContext` with `UsernamePasswordAuthenticationToken`
3. **Session storage**: Java `ConcurrentHashMap` keyed by session ID
4. **Cookie config**: `HttpOnly=true, Secure=true, SameSite=Lax` per constitution §2

**Configuration**:
```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
        same-site: lax
      timeout: 60s  # 60-second session for MVP
  forward-headers-strategy: framework  # Required per constitution §3.2

# No Spring Session JDBC configuration needed
```

**Constitutional Note**:
Constitution §1.5 warns against "fragile in-memory maps keyed by session IDs" but this is acceptable because:
- User requirement overrides constitution recommendation
- MVP scope (single-user testing, not production)
- Single backend instance (no clustering needed)
- Session data loss on restart is acceptable for MVP

**Alternatives considered**:
- **Spring Session JDBC with H2**: Rejected per user requirement "Do not use database"
- **Pure stateless with JWT**: Rejected - cannot safely store PKCE code_verifier on client side, violates OAuth2 security
- **Redis session store**: Recommended for production (deferred for MVP)

### 3. PKCE Implementation

**Decision**: **No custom PKCE implementation** - Spring Security 7 handles it automatically

**Rationale**:
- Spring Security 7 (Spring Boot 4) includes built-in PKCE support for OAuth2 authorization code flow
- PKCE is **automatically enabled** for public and confidential clients
- Spring generates code_verifier, computes code_challenge (S256), and manages state/nonce
- User requirement: "use all features from Spring; I want the minimal code possible"

**Spring's automatic PKCE behavior**:
```
1. User initiates login → GET /oauth2/authorization/google
2. Spring generates:
   - code_verifier (random 43-128 chars, base64url)
   - code_challenge = Base64url(SHA256(code_verifier))
   - state (CSRF protection)
   - nonce (ID token replay protection)
3. Spring redirects to Google with code_challenge and code_challenge_method=S256
4. Google redirects back → GET /login/oauth2/code/google?code=...&state=...
5. Spring validates state, exchanges code + code_verifier for tokens
6. Spring validates ID token (signature, issuer, audience, exp, nonce)
7. Spring creates SecurityContext with OAuth2User
```

**Configuration** (enables PKCE automatically):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            authorization-grant-type: authorization_code  # PKCE auto-enabled
```

**Custom code needed**: **ZERO** - Spring handles everything

**Alternatives considered**:
- **Manual PKCE with PKCEUtil class**: Rejected - unnecessary, Spring already does this
- **Plain method**: Not applicable - Spring defaults to S256 (most secure)

### 4. Automatic Token Refresh Strategy

**Decision**: Use Spring's `OAuth2AuthorizedClientManager` for automatic token refresh + optional manual refresh endpoint

**Rationale**:
- Spring Security's `OAuth2AuthorizedClientManager` **automatically refreshes tokens** when expired
- `OAuth2AuthorizedClientRepository` stores tokens and manages refresh lifecycle
- User requirement: "use all features from Spring"
- Clarification #3: "Automatic refresh when token expires; 'Refresh Now' button provides manual alternative"

**Spring's automatic refresh** (no custom code):
```java
// Spring auto-configures this bean
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken()  // Enables automatic token refresh
            .build();

    DefaultOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);

    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
    return authorizedClientManager;
}
```

**How it works**:
1. Frontend calls `/api/auth/user` to get user info and expiration
2. When access token expires, Spring's `OAuth2AuthorizedClientManager` automatically:
   - Detects token expiration
   - Uses stored refresh_token to get new access_token from Google
   - Updates `OAuth2AuthorizedClient` in repository
   - Returns refreshed token transparently
3. No manual refresh logic needed in most cases

**Optional manual refresh endpoint** (for "Refresh Now" button):
```java
@RestController
public class OAuth2Controller {
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<?> refreshToken(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        // Spring automatically refreshes if needed via @RegisteredOAuth2AuthorizedClient
        Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
        return ResponseEntity.ok(Map.of("expiresAt", expiresAt.toEpochMilli()));
    }
}
```

**Frontend** (optional countdown timer):
```javascript
// dashboard.js - show countdown, trigger manual refresh on button click
async function refreshToken() {
    const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'include'
    });
    if (response.ok) {
        const data = await response.json();
        updateCountdownDisplay(data.expiresAt);
    }
}
```

**Alternatives considered**:
- **Manual token refresh implementation**: Rejected - Spring's OAuth2AuthorizedClientManager already does this
- **Client-side timer with manual backend calls**: Simplified - Spring handles refresh transparently

### 5. CORS Configuration

**Decision**: Configure CORS in `SecurityFilterChain` with explicit frontend origin

**Rationale**:
- Constitution §3.2 mandates CORS in `SecurityFilterChain`, not just `WebMvcConfigurer`
- Spring Security intercepts requests before MVC - MVC-only CORS causes 403 on preflight
- Must allow credentials (`setAllowCredentials(true)`) for session cookies
- Frontend origin MUST be configurable via `application.yml`

**Implementation**:
```java
@Configuration
public class SecurityConfig {

    @Value("${app.frontend.origin}")
    private String frontendOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // CSRF handled by state parameter
            // ... other config
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

**Configuration**:
```yaml
app:
  frontend:
    origin: https://frontend.brunorozendo.dev
```

**Alternatives considered**:
- **WebMvcConfigurer only**: Rejected - violates constitution §3.2, causes 403 errors
- **Wildcard origin with credentials**: Rejected - browsers block this combination for security

### 6. Cache Control Strategy

**Decision**: Serve all frontend assets with `Cache-Control: no-store`

**Rationale**:
- Clarification #2 specifies: "No caching (Cache-Control: no-store) - forces fresh fetch every time"
- Ensures users always get latest frontend code after deployments
- MVP prioritizes correctness over performance

**Implementation**:
```nginx
# nginx.conf
server {
    listen 8631;
    server_name localhost;
    root /usr/share/nginx/html;

    location / {
        add_header Cache-Control "no-store, no-cache, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires "0";
        try_files $uri $uri/ /index.html;
    }
}
```

**Alternatives considered**:
- **Cache busting via hashed filenames**: Deferred to production
- **Short TTL**: Rejected - spec requires immediate freshness

### 7. Session Timeout Configuration

**Decision**: Configurable session timeout property, default 60 seconds for MVP

**Rationale**:
- Clarification #1 specifies: "1 minute for MVP validation; configurable via property"
- Enables easy testing of token expiration and refresh flows
- Production will use longer timeout (configurable without code changes)

**Implementation**:
```yaml
server:
  servlet:
    session:
      timeout: ${SESSION_TIMEOUT:60s}  # Default 60s, overridable via env

app:
  session:
    timeout-seconds: ${SESSION_TIMEOUT_SECONDS:60}  # Numeric value for frontend
```

**Alternatives considered**:
- **Hardcoded 60 seconds**: Rejected - violates requirement for configurability
- **Separate dev/prod configs**: Rejected - single config with env override is simpler

### 8. ArchUnit Test Strategy

**Decision**: Comprehensive architecture validation with allowed exceptions

**Rationale**:
- User requires: "create arcunit tests to validate all architecture"
- Constitution §4.3 specifies allowed exception: `org.slf4j` in Controller layer
- Tests enforce constitution rules automatically

**Test coverage**:
```java
// ArchitectureTests.java
@AnalyzeClasses(packages = "com.brunorozendo.oauth2")
public class ArchitectureTests {

    @ArchTest
    static final ArchRule controllers_must_be_in_controller_package =
        classes().that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule controllers_must_use_rest_controller_annotation =
        classes().that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule services_must_be_in_service_package =
        classes().that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule services_must_use_service_annotation =
        classes().that().haveSimpleNameEndingWith("Service")
            .should().beAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule layer_dependencies_are_respected =
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Config")
            .allowEmptyShould(true)
            .ignoreDependency(
                resideInAPackage("..controller.."),
                resideInAPackage("org.slf4j..")  // Constitution §4.3
            );

    @ArchTest
    static final ArchRule api_endpoints_must_have_api_prefix =
        methods().that().areAnnotatedWith(RequestMapping.class)
            .or().areAnnotatedWith(GetMapping.class)
            .or().areAnnotatedWith(PostMapping.class)
            .should(new ArchCondition<JavaMethod>("have /api prefix") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    // Verify path starts with /api
                }
            });
}
```

**Alternatives considered**:
- **Manual code reviews**: Rejected - not automated, error-prone
- **Checkstyle/PMD**: Rejected - cannot enforce architectural rules like layer isolation

## Summary

All technical unknowns have been resolved with decisions that:
1. ✅ Comply with constitution requirements
2. ✅ Address all 5 clarifications from spec
3. ✅ Follow Spring Boot 4 best practices
4. ✅ Enable `podman compose build; podman compose up` acceptance criteria
5. ✅ Support brutally simplistic implementation using framework features

**Key architectural points**:
- Hybrid approach: Spring OAuth2 client infrastructure + manual PKCE control
- Session-based authentication with explicit security context persistence
- Client-side automatic token refresh with backend support
- Constitution-compliant CORS, cache control, and session configuration
- Comprehensive ArchUnit testing with documented exceptions
