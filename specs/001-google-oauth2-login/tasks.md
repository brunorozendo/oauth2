# Tasks: Google OAuth2 Login with Full Authentication Flow

**Input**: Design documents from `/specs/001-google-oauth2-login/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-spec.yaml

**Design Philosophy**: Brutally simplistic - leverage Spring Boot auto-configuration and Spring Security's OAuth2 features. Minimize custom code (~90 lines total), maximize framework usage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/`, `frontend/src/`
- Backend: `backend/src/main/java/com/brunorozendo/oauth2/`
- Frontend: `frontend/src/`
- Resources: `backend/src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create backend directory structure: `backend/src/main/java/com/brunorozendo/oauth2/{config,controller}`, `backend/src/main/resources`, `backend/src/test/java/com/brunorozendo/oauth2/architecture`
- [X] T002 Create frontend directory structure: `frontend/src/{js,css,assets}`
- [X] T003 Create e2e-tests directory structure: `e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e`
- [X] T004 Initialize Gradle project with wrapper in backend/ (Gradle 9.3.1)
- [X] T005 [P] Create backend/build.gradle with Spring Boot 4.0.2, Spring Security OAuth2 client, ArchUnit dependencies
- [X] T006 [P] Create backend/.gitignore for Java/Gradle build artifacts
- [X] T007 [P] Create e2e-tests/build.gradle with Selenium WebDriver and Testcontainers dependencies

**Checkpoint**: Project structure initialized, build tools configured

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 Create backend/src/main/java/com/brunorozendo/oauth2/OAuth2Application.java with @SpringBootApplication
- [X] T009 Create backend/src/main/resources/application.yml with server port 9103, session timeout 60s, session cookie config (HttpOnly, Secure, SameSite=Lax), forward-headers-strategy: framework
- [X] T010 [P] Configure Spring Security OAuth2 client registration for Google in application.yml (client-id from env, client-secret from env, scope: openid,email,profile, redirect-uri: {baseUrl}/login/oauth2/code/{registrationId})
- [X] T011 [P] Configure Google provider in application.yml (issuer-uri: https://accounts.google.com for auto-discovery)
- [X] T012 [P] Add app.frontend.origin property in application.yml: https://frontend.brunorozendo.dev
- [X] T013 [P] Add logging configuration in application.yml (DEBUG level for com.brunorozendo.oauth2 and org.springframework.security)
- [X] T014 Create backend/src/main/java/com/brunorozendo/oauth2/config/SecurityConfig.java with @Configuration and @EnableWebSecurity annotations
- [X] T015 Implement SecurityFilterChain bean in SecurityConfig.java: configure CORS (allow frontend origin with credentials), disable CSRF, enable oauth2Login() with defaults
- [X] T016 Implement CorsConfigurationSource bean in SecurityConfig.java: allow frontend origin, allow credentials, allow GET/POST/OPTIONS methods

**What Spring Security Handles Automatically** (zero custom code required):
- ✅ **FR-003**: PKCE code_verifier and code_challenge generation (S256 method)
- ✅ **FR-005**: State parameter validation on callback (CSRF protection)
- ✅ **FR-006**: Authorization code exchange for tokens at `/login/oauth2/code/google`
- ✅ **FR-007**: ID token validation (JWT signature, issuer, audience, expiration, nonce)
- ✅ **FR-008**: User profile fetch from Google's userinfo endpoint
- ✅ **FR-009**: SecurityContext creation with OAuth2User principal

See [research.md](research.md) for detailed explanation of Spring Security's OAuth2 auto-configuration.

**Checkpoint**: Foundation ready - Spring Security OAuth2 auto-configuration complete, user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Google OAuth2 Login Flow (Priority: P1) 🎯 MVP

**Goal**: Enable users to authenticate via Google OAuth2 and access the dashboard with an authenticated session

**Independent Test**: Click "Sign in with Google" on login page, complete Google consent flow, verify redirect to dashboard showing user profile information (name, email, avatar)

**What Spring handles automatically for US1**:
- ✅ PKCE generation (code_verifier, code_challenge S256)
- ✅ OAuth2 authorization redirect to Google at `/oauth2/authorization/google`
- ✅ OAuth2 callback handling at `/login/oauth2/code/google`
- ✅ Token exchange and ID token validation
- ✅ User info fetch from Google
- ✅ SecurityContext creation with OAuth2User principal

### Implementation for User Story 1

- [X] T017 [P] [US1] Create frontend/src/index.html with "Sign in with Google" button
- [X] T018 [P] [US1] Create frontend/src/js/auth.js: implement login button click handler that redirects to `/oauth2/authorization/google`
- [X] T019 [P] [US1] Create frontend/src/css/style.css with basic styling for login page (centered container, Google button styling)
- [X] T020 [P] [US1] Create frontend/src/dashboard.html with placeholders for user profile (avatar, name, email), countdown timer, and buttons
- [X] T021 [US1] Configure SecurityConfig.java to add defaultSuccessUrl("/dashboard.html") to oauth2Login() configuration
- [X] T022 [US1] Add error handling in auth.js: parse URL query params for error, display error message with "Try Again" link if present

**Checkpoint**: User Story 1 complete - users can login with Google and see dashboard. This is a functional MVP.

---

## Phase 4: User Story 2 + User Story 5 - Dashboard & Session Expiry (Priority: P2)

**Combined Goal**: Display user profile with token countdown on dashboard (US2) + automatically redirect expired sessions to login (US5)

**Independent Test**: Login successfully, observe dashboard displays profile (name, email, avatar) with live countdown timer. Wait for session to expire (60 seconds), verify automatic redirect to login page.

### Implementation for User Story 2 (Dashboard Display)

- [X] T023 [P] [US2] Create backend/src/main/java/com/brunorozendo/oauth2/controller/OAuth2Controller.java with @RestController and @RequestMapping("/api/auth")
- [X] T024 [US2] Implement GET /api/auth/user endpoint in OAuth2Controller.java: use @AuthenticationPrincipal OAuth2User and @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient parameters, return user attributes + token expiresAt
- [X] T025 [P] [US2] Create frontend/src/js/dashboard.js: implement fetchUserInfo() function that calls /api/auth/user
- [X] T026 [US2] Implement displayUserProfile() function in dashboard.js: populate avatar, name, email from user data, use defaults for missing fields ("Unknown User", placeholder avatar)
- [X] T027 [US2] Implement startCountdown(expiresAt) function in dashboard.js: calculate remaining time, update countdown display every second
- [X] T028 [US2] Add console.debug logging in dashboard.js when user profile has missing fields
- [X] T029 [P] [US2] Create frontend/src/assets/default-avatar.png (placeholder avatar image)

### Implementation for User Story 5 (Session Expiry)

- [X] T030 [US5] Add error handling in dashboard.js: if /api/auth/user returns 401, redirect to index.html with error=session_expired
- [X] T031 [US5] Update auth.js to display "Session expired" message when error=session_expired param is present

**Checkpoint**: User Story 2 and 5 complete - dashboard shows profile with countdown, expired sessions redirect to login

---

## Phase 5: User Story 3 - Automatic and Manual Token Refresh (Priority: P2)

**Goal**: Automatically refresh tokens when expired + provide manual "Refresh Now" button

**Independent Test**: Login, wait for token to expire, verify automatic refresh updates countdown. Alternatively, click "Refresh Now" button before expiry, verify manual refresh works.

**Note**: Spring's OAuth2AuthorizedClientManager handles automatic token refresh when accessing OAuth2AuthorizedClient. We only need to provide UI for manual refresh and trigger token access.

### Implementation for User Story 3

- [X] T032 [P] [US3] Implement POST /api/auth/refresh endpoint in OAuth2Controller.java: inject @RegisteredOAuth2AuthorizedClient, return new expiresAt (Spring auto-refreshes if expired)
- [X] T033 [US3] Implement refreshToken() function in dashboard.js: POST to /api/auth/refresh, update countdown on success, redirect to login on failure
- [X] T034 [US3] Add "Refresh Now" button click handler in dashboard.js: disable button, show loading indicator, call refreshToken()
- [X] T035 [US3] Implement automatic refresh in dashboard.js: when countdown reaches 0, call refreshToken() automatically
- [X] T036 [US3] Add visual feedback in dashboard.js: disable "Refresh Now" button and show loading spinner during refresh operations

**Checkpoint**: User Story 3 complete - automatic and manual token refresh working

---

## Phase 6: User Story 4 - Logout and Session Termination (Priority: P3)

**Goal**: Allow users to logout, terminating session and revoking tokens

**Independent Test**: Login successfully, click "Logout" button, verify redirect to login page. Attempt to access dashboard, verify redirect to login (session invalid).

### Implementation for User Story 4

- [X] T037 [US4] Implement POST /api/auth/logout endpoint in OAuth2Controller.java: invalidate HttpSession, return success message
- [X] T038 [P] [US4] Implement logout() function in dashboard.js: POST to /api/auth/logout, redirect to index.html on success
- [X] T039 [US4] Add "Logout" button click handler in dashboard.js: call logout() function
- [X] T040 [US4] Add token revocation in OAuth2Controller logout endpoint: use RestTemplate to POST access_token to https://oauth2.googleapis.com/revoke, log errors if revocation fails, continue with session invalidation regardless (per FR-022)

**Checkpoint**: User Story 4 complete - logout functionality working

---

## Phase 7: Frontend Polish & Configuration

**Purpose**: Complete frontend implementation and configuration

- [X] T041 [P] Create frontend/nginx.conf: listen on port 8631, root /usr/share/nginx/html, add Cache-Control: no-store headers, try_files directive for SPA routing
- [X] T042 [P] Enhance frontend/src/css/style.css: dashboard layout, user profile card, countdown timer styling, button styles, responsive design
- [X] T043 [P] Add loading states and error messages in dashboard.js for all API calls
- [X] T044 [P] Add proper HTML meta tags in index.html and dashboard.html (charset, viewport, title)

**Checkpoint**: Frontend complete and polished

---

## Phase 8: Containerization

**Purpose**: Docker/Podman container setup for backend and frontend

- [X] T045 Create backend/Dockerfile with multi-stage build: builder stage (eclipse-temurin:21-jdk-alpine, ./gradlew bootJar), runtime stage (eclipse-temurin:21-jre-alpine, COPY jar, EXPOSE 9103)
- [X] T046 [P] Create frontend/Dockerfile: FROM nginx:alpine, COPY nginx.conf to /etc/nginx/conf.d/default.conf, COPY src/ to /usr/share/nginx/html/, EXPOSE 8631
- [X] T047 Create docker-compose.yml at repository root: define backend and frontend services, expose ports 9103 and 8631, set environment variables from .env, configure network
- [X] T048 Verify .env file exists with GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET

**Checkpoint**: Containerization complete

---

## Phase 9: Testing

**Purpose**: Architecture validation and end-to-end testing

### ArchUnit Tests

- [X] T049 Create backend/src/test/java/com/brunorozendo/oauth2/architecture/ArchitectureTests.java with @AnalyzeClasses annotation
- [X] T050 [P] Implement ArchUnit rule in ArchitectureTests.java: controllers must be in controller package and use @RestController
- [X] T051 [P] Implement ArchUnit rule in ArchitectureTests.java: config classes must be in config package and use @Configuration
- [X] T052 [P] Implement ArchUnit rule in ArchitectureTests.java: API endpoints must have /api prefix (verify @RequestMapping values)
- [X] T053 [P] Implement ArchUnit rule in ArchitectureTests.java: layer dependencies are respected (controller -> config allowed, allow org.slf4j in controller per constitution)
- [X] T054 Run ArchUnit tests with `./gradlew test --tests '*ArchitectureTests'` and verify all pass

### End-to-End Tests

- [X] T055 Create e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e/OAuth2LoginFlowTest.java with Selenium WebDriver and Testcontainers setup
- [X] T056 [P] Implement E2E test in OAuth2LoginFlowTest.java: full login flow (click login button, verify redirect to Google, complete mock OAuth flow, verify dashboard loads with user info)
- [X] T057 [P] Create e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e/TokenRefreshTest.java: test automatic and manual token refresh
- [X] T058 [P] Create e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e/LogoutTest.java: test logout and session termination
- [X] T059 [P] Create e2e-tests/src/test/java/com/brunorozendo/oauth2/e2e/SessionExpiryTest.java: test session expiry redirect

**Checkpoint**: All tests implemented

---

## Phase 10: Final Verification & Deployment

**Purpose**: Verify complete application works as expected

- [X] T060 Run ArchUnit tests: `cd backend && ./gradlew test --tests '*ArchitectureTests'` and verify all pass
- [X] T061 Run E2E tests: `cd e2e-tests && ./gradlew test` and verify all pass
- [X] T062 Build containers: `podman compose build --no-cache` and verify successful build with no errors
- [X] T063 Start application: `podman compose up -d` and verify both containers start successfully
- [X] T064 Verify backend health: `curl https://backend.brunorozendo.dev/api/auth/user` returns 401 (not authenticated)
- [X] T065 Verify frontend loads: `curl https://frontend.brunorozendo.dev/` returns HTML with login button
- [ ] T066 Manual integration test: Open https://frontend.brunorozendo.dev, complete full OAuth2 login flow with real Google account, verify dashboard displays correctly
- [ ] T067 Manual token refresh test: On dashboard, click "Refresh Now" button, verify countdown resets
- [ ] T068 Manual logout test: Click "Logout" button, verify redirect to login page
- [ ] T069 Manual session expiry test: Wait 60 seconds on dashboard, verify automatic redirect to login
- [ ] T070 Stop application: `podman compose down` and verify clean shutdown

**Checkpoint**: ✅ Application fully functional - acceptance criteria met

---

## Implementation Strategy

### MVP Scope (User Story 1 only)

**Minimum viable product**:
- Tasks T001-T022 (Setup + Foundational + US1)
- Result: Users can login with Google and access dashboard
- Total: 22 tasks

### Incremental Delivery Order

1. **MVP** (US1): Tasks T001-T022
2. **Enhanced UX** (US2, US5): Tasks T023-T031 (Dashboard display + Session expiry)
3. **Session Management** (US3): Tasks T032-T036 (Token refresh)
4. **Security** (US4): Tasks T037-T040 (Logout)
5. **Production Ready**: Tasks T041-T070 (Polish, containers, tests, verification)

### Parallel Execution Opportunities

**Phase 1 (Setup)**: All tasks can run in parallel (T001-T007)

**Phase 2 (Foundational)**:
- Parallel group 1: T009-T013 (configuration files)
- Sequential: T014-T016 (SecurityConfig implementation)

**Phase 3 (US1)**:
- Parallel group 1: T017-T020 (frontend HTML/JS/CSS files)
- Sequential: T021-T022 (SecurityConfig update, error handling)

**Phase 4 (US2+US5)**:
- Parallel group 1: T023, T025-T029, T029 (controller + frontend JS + assets)
- Sequential: T024 (depends on T023), T030-T031 (error handling)

**Phase 5 (US3)**:
- Parallel group 1: T032 (backend endpoint)
- Sequential: T033-T036 (frontend implementation depending on T032)

**Phase 6 (US4)**:
- Parallel: T037-T038 (backend + frontend)
- Sequential: T039-T040 (integration)

**Phase 7 (Polish)**: All tasks can run in parallel (T041-T044)

**Phase 8 (Containerization)**:
- Parallel: T045-T046 (Dockerfiles)
- Sequential: T047-T048 (compose + env)

**Phase 9 (Testing)**:
- ArchUnit: All T050-T053 can run in parallel after T049
- E2E: All T056-T059 can run in parallel after T055

**Phase 10 (Verification)**: Must run sequentially (T060-T070)

---

## Task Dependencies

### User Story Completion Order

```
US1 (P1) → [US2 (P2), US3 (P2), US4 (P3), US5 (P2)]

Legend:
- US1 must complete first (foundation)
- US2, US3, US4, US5 can be implemented in parallel after US1
- US5 integrates with US2 (session expiry + dashboard)
```

### Critical Path

```
Setup (T001-T007)
  ↓
Foundational (T008-T016)
  ↓
US1 MVP (T017-T022) ← Blocking for all other user stories
  ↓
┌─────────────┬──────────────┬──────────────┬──────────────┐
│ US2+US5     │ US3          │ US4          │ Polish       │
│ (T023-T031) │ (T032-T036)  │ (T037-T040)  │ (T041-T044)  │
└─────────────┴──────────────┴──────────────┴──────────────┘
  ↓
Containerization (T045-T048)
  ↓
Testing (T049-T059)
  ↓
Verification (T060-T070)
```

---

## Independent Test Criteria

### User Story 1 (Login Flow)
✅ **Test**: Click "Sign in with Google", complete consent, land on dashboard with profile
✅ **Success**: User sees name, email, avatar on dashboard

### User Story 2 (Dashboard)
✅ **Test**: Login, observe dashboard displays profile and countdown timer
✅ **Success**: Profile shows, timer counts down accurately

### User Story 3 (Token Refresh)
✅ **Test**: Login, click "Refresh Now" OR wait for auto-refresh
✅ **Success**: Countdown resets, no re-login required

### User Story 4 (Logout)
✅ **Test**: Login, click "Logout", attempt dashboard access
✅ **Success**: Redirects to login, dashboard inaccessible

### User Story 5 (Session Expiry)
✅ **Test**: Login, wait 60 seconds, attempt dashboard action
✅ **Success**: Auto-redirects to login with expiry message

---

## Task Summary

**Total Tasks**: 70
- Setup: 7 tasks
- Foundational: 9 tasks
- US1 (P1): 6 tasks
- US2+US5 (P2): 9 tasks
- US3 (P2): 5 tasks
- US4 (P3): 4 tasks
- Frontend Polish: 4 tasks
- Containerization: 4 tasks
- Testing: 11 tasks
- Verification: 11 tasks

**Parallel Opportunities**: 35 tasks can run in parallel (marked with [P])

**Code Estimate**: ~90 lines of custom Java code (SecurityConfig ~50 lines, OAuth2Controller ~30 lines, main class ~10 lines) + ~200 lines of frontend JS + configuration files

**MVP Scope**: First 22 tasks deliver functional Google OAuth2 login

**Acceptance Criteria**: Task T063 (`podman compose up -d`) must successfully start application with no errors
