# Quickstart: Google OAuth2 Login Implementation

**Feature**: 001-google-oauth2-login
**Date**: 2026-02-17
**Audience**: Developers implementing this feature

## Prerequisites

Before starting implementation, ensure you have:

1. ✅ **Google OAuth2 Credentials**
   - File: `/Users/bruno/Developer/projects/oauth2/client_secret_*.json`
   - Client ID and Secret available in `.env` file

2. ✅ **Environment Variables**
   - File: `/Users/bruno/Developer/projects/oauth2/.env`
   - Contains: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

3. ✅ **Proxy Setup**
   - Frontend: `https://frontend.brunorozendo.dev` → `localhost:8631`
   - Backend: `https://backend.brunorozendo.dev` → `localhost:9103`
   - DNS/tunnel already configured (use DNS names, not host:port)

4. ✅ **Tools Installed**
   - Podman (or Docker)
   - Java 21 (for local development)
   - Gradle 9.3.1 (wrapper included)

## Implementation Steps

### Step 1: Create Project Structure (Minimal)

```bash
cd /Users/bruno/Developer/projects/oauth2

# Create backend directories (MINIMAL - no service, model, util packages)
mkdir -p backend/src/main/java/com/brunorozendo/oauth2/{config,controller}
mkdir -p backend/src/main/resources
mkdir -p backend/src/test/java/com/brunorozendo/oauth2/architecture

# Create frontend directories
mkdir -p frontend/src/{js,css,assets}

# Create E2E test directories
mkdir -p e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e
```

**Note**: No `service/`, `model/`, or `util/` packages needed - Spring Security handles everything automatically.

### Step 2: Backend Setup

#### 2.1 Create `backend/build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.2'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'groovy'  // For Spock tests
}

group = 'com.brunorozendo'
version = '1.0.0'
sourceCompatibility = '21'

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'

    // Spock framework
    testImplementation 'org.spockframework:spock-core:2.4-groovy-5.0'
    testImplementation 'org.spockframework:spock-spring:2.4-groovy-5.0'
    testImplementation 'org.apache.groovy:groovy-all:5.0.3'

    // ArchUnit
    testImplementation 'com.tngtech.archunit:archunit:1.3.0'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
}

test {
    useJUnitPlatform()
}
```

#### 2.2 Create `backend/src/main/resources/application.yml`

```yaml
server:
  port: 9103
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
        same-site: lax
      timeout: ${SESSION_TIMEOUT:60s}
  forward-headers-strategy: framework  # Required per constitution §3.2

spring:
  application:
    name: oauth2-backend

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
            redirect-uri: https://backend.brunorozendo.dev/api/auth/callback
            authorization-grant-type: authorization_code
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
            jwk-set-uri: https://www.googleapis.com/oauth2/v3/certs

app:
  frontend:
    origin: https://frontend.brunorozendo.dev
  session:
    timeout-seconds: ${SESSION_TIMEOUT_SECONDS:60}

logging:
  level:
    com.brunorozendo.oauth2: DEBUG
    org.springframework.security: DEBUG
```

#### 2.3 Create `backend/Dockerfile`

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 9103

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 3: Frontend Setup

#### 3.1 Create `frontend/src/index.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>OAuth2 Login</title>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <div class="container">
        <h1>OAuth2 Google Login</h1>
        <button id="login-button">Sign in with Google</button>
        <div id="error-message" class="error"></div>
    </div>

    <script src="/js/auth.js"></script>
</body>
</html>
```

#### 3.2 Create `frontend/src/dashboard.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard</title>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <div class="container">
        <h1>Dashboard</h1>
        <div id="user-profile">
            <img id="user-avatar" src="" alt="User Avatar">
            <h2 id="user-name"></h2>
            <p id="user-email"></p>
        </div>
        <div id="token-info">
            <p>Token expires in: <span id="countdown">--</span></p>
            <button id="refresh-button">Refresh Now</button>
        </div>
        <button id="logout-button">Logout</button>
    </div>

    <script src="/js/dashboard.js"></script>
</body>
</html>
```

#### 3.3 Create `frontend/nginx.conf`

```nginx
server {
    listen 8631;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # No caching per clarification #2
    location / {
        add_header Cache-Control "no-store, no-cache, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires "0";
        try_files $uri $uri/ /index.html;
    }
}
```

#### 3.4 Create `frontend/Dockerfile`

```dockerfile
FROM nginx:alpine

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static files
COPY src/ /usr/share/nginx/html/

EXPOSE 8631

CMD ["nginx", "-g", "daemon off;"]
```

### Step 4: Docker Compose Setup

Create `docker-compose.yml` in project root:

```yaml
version: '3.8'

services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: oauth2-backend
    ports:
      - "9103:9103"
    environment:
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
      - SESSION_TIMEOUT=60s
      - SESSION_TIMEOUT_SECONDS=60
    networks:
      - oauth2-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: oauth2-frontend
    ports:
      - "8631:8631"
    depends_on:
      - backend
    networks:
      - oauth2-network

networks:
  oauth2-network:
    driver: bridge
```

### Step 5: Implementation Order

Follow this sequence to implement the feature:

1. **Phase 1: Core Infrastructure** (Priority P1)
   - ✅ SecurityConfig (CORS, CSRF, session security)
   - ✅ PKCEUtil (code verifier/challenge generation)
   - ✅ Model classes (PKCESession, UserProfile, TokenSet)

2. **Phase 2: OAuth2 Flow** (Priority P1)
   - ✅ OAuth2Controller:
     - `/api/auth/login/google` - Initiate login
     - `/api/auth/callback` - Handle callback
   - ✅ OAuth2Service:
     - Token exchange
     - ID token validation
     - User info fetch

3. **Phase 3: Session Management** (Priority P2)
   - ✅ `/api/auth/user` - Get current user
   - ✅ `/api/auth/refresh` - Refresh access token
   - ✅ `/api/auth/logout` - Logout and revoke

4. **Phase 4: Frontend** (Priority P2)
   - ✅ Login page (index.html + auth.js)
   - ✅ Dashboard (dashboard.html + dashboard.js)
   - ✅ Automatic token refresh countdown

5. **Phase 5: Testing** (Priority P3)
   - ✅ ArchUnit tests (architecture validation)
   - ✅ Spock unit tests (service logic)
   - ✅ E2E tests (Selenium)

### Step 6: Build and Run

#### Local Development

```bash
# Backend (terminal 1)
cd backend
./gradlew bootRun

# Frontend (terminal 2)
cd frontend
# Serve with any static file server, e.g., Python
python3 -m http.server 8631 --directory src

# Or use nginx locally
nginx -c $(pwd)/nginx.conf -p $(pwd)
```

#### Production (Podman Compose)

```bash
# Build and run (acceptance criteria)
podman compose build
podman compose up

# Or in one command
podman compose build && podman compose up

# View logs
podman compose logs -f backend
podman compose logs -f frontend

# Stop
podman compose down
```

### Step 7: Verify Setup

1. **Check containers running**:
   ```bash
   podman ps
   # Should see oauth2-backend and oauth2-frontend
   ```

2. **Test backend health**:
   ```bash
   curl https://backend.brunorozendo.dev/api/auth/login/google
   # Should return JSON with authorizationUrl
   ```

3. **Test frontend access**:
   ```bash
   curl https://frontend.brunorozendo.dev/
   # Should return HTML with login button
   ```

4. **Complete login flow**:
   - Open `https://frontend.brunorozendo.dev/`
   - Click "Sign in with Google"
   - Complete Google consent
   - Verify redirect to dashboard with profile

## Development Workflow

### Running Tests

```bash
# All tests
cd backend
./gradlew test

# Only ArchUnit tests
./gradlew test --tests '*ArchitectureTests'

# Only Spock tests
./gradlew test --tests '*Spec'

# E2E tests
cd ../e2e-tests
./gradlew test
```

### Debugging

1. **Enable debug logging** (already configured in application.yml):
   ```yaml
   logging:
     level:
       com.brunorozendo.oauth2: DEBUG
       org.springframework.security: DEBUG
   ```

2. **Session debugging**: Add breakpoints in controllers to inspect HttpSession attributes:
   ```java
   // In OAuth2Controller
   PKCESession pkceSession = (PKCESession) httpSession.getAttribute("pkce_session");
   logger.debug("PKCE Session: {}", pkceSession);
   ```

3. **Browser DevTools**:
   - Check Network tab for API calls
   - Check Console tab for JavaScript errors
   - Check Application > Cookies for JSESSIONID

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| 403 on login | CORS not configured in SecurityFilterChain | Verify SecurityConfig follows constitution §3.2 |
| Session not persisting | Missing SecurityContext save | Implement constitution §3.3 requirements |
| Redirect uses http:// | Missing forward-headers-strategy | Add to application.yml per constitution |
| Frontend not updating | Browser cache | Clear cache or use Ctrl+F5 (no-store should prevent this) |
| Token refresh fails | Missing refresh_token | Check Google OAuth2 config includes offline access |

## Configuration Checklist

Before running, verify:

- [ ] `.env` file exists with GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET
- [ ] Google OAuth2 credentials have correct redirect URIs:
  - ✅ `https://backend.brunorozendo.dev/api/auth/callback`
- [ ] Google OAuth2 credentials have correct JavaScript origins:
  - ✅ `https://frontend.brunorozendo.dev`
- [ ] Proxy/tunnel routing:
  - ✅ `frontend.brunorozendo.dev` → `localhost:8631`
  - ✅ `backend.brunorozendo.dev` → `localhost:9103`
- [ ] Docker/Podman running and accessible

## Success Criteria Verification

After implementation, verify all success criteria from spec.md:

- [ ] **SC-001**: Login flow completes in under 10 seconds
- [ ] **SC-002**: PKCE code_verifier and state validated (100%)
- [ ] **SC-003**: Dashboard displays profile within 2 seconds
- [ ] **SC-004**: Token countdown updates in real-time (±1 second accuracy)
- [ ] **SC-005**: Token refresh (auto/manual) completes in under 3 seconds
- [ ] **SC-006**: Logout fully invalidates session and revokes tokens (100%)
- [ ] **SC-007**: Expired session redirects to login within 1 second
- [ ] **SC-008**: Error messages display within 2 seconds
- [ ] **SC-009**: Zero tokens exposed to frontend or browser storage
- [ ] **SC-010**: Fresh code served immediately (Cache-Control: no-store)
- [ ] **SC-011**: All auth events logged with structured format

## Next Steps

After quickstart setup is complete:

1. ✅ **Phase 0 Complete**: Research resolved, ready for implementation
2. ✅ **Phase 1 Complete**: Data model and contracts defined
3. ⏭️ **Phase 2**: Run `/speckit.tasks` to generate detailed task breakdown
4. ⏭️ **Implementation**: Execute tasks in priority order
5. ⏭️ **Testing**: Verify all ArchUnit tests pass
6. ⏭️ **Validation**: Run `podman compose build && podman compose up`

## Reference Documentation

- **Specification**: `specs/001-google-oauth2-login/spec.md`
- **Research Notes**: `specs/001-google-oauth2-login/research.md`
- **Data Model**: `specs/001-google-oauth2-login/data-model.md`
- **API Contract**: `specs/001-google-oauth2-login/contracts/api-spec.yaml`
- **OAuth2 Guide**: `/Users/bruno/Developer/projects/oauth2/oauth2.md`
- **Constitution**: `.specify/memory/constitution.md`
