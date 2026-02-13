# Tasks: OAuth2 Implementation

**Input**: Design documents from `/specs/001-oauth2-spring-nginx/`
**Branch**: `001-oauth2-spring-nginx`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create project structure (backend, frontend, e2e-tests directories)
- [x] T002 Initialize Spring Boot 4.0.x project in `backend/` with Gradle 9.x
- [x] T003 [P] Configure backend `build.gradle` dependencies (Web, Security, Session)
- [x] T004 [P] Create frontend structure and `nginx.conf` in `frontend/`
- [x] T005 [P] Create `docker-compose.yml` for orchestration (Backend + Frontend)
- [x] T006 Setup `.env` loading and configuration properties in Backend

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure for Manual PKCE Flow

- [x] T007 Implement `PKCEUtil` class in `backend/src/main/java/com/bunorozendo/auth/util/PKCEUtil.java`
- [x] T008 Implement `PKCESession` model in `backend/src/main/java/com/bunorozendo/auth/model/PKCESession.java`
- [x] T009 Implement `PKCESessionStore` (InMemory) in `backend/src/main/java/com/bunorozendo/auth/service/PKCESessionStore.java`
- [x] T010 Configure `SecurityConfig` to permit `/auth/**` and configure CORS for Frontend
- [x] T011 Create Frontend API Client wrapper (auth.js) in `frontend/src/js/auth.js`

## Phase 3: User Story 1 - Secure Login with Google (Priority: P1) 🎯 MVP

**Goal**: User can click login, redirect to Google, and return authenticated.

**Independent Test**: Click Login -> Google Consent -> Redirect to Dashboard.

### Implementation for User Story 1

- [x] T012 [US1] Implement `initiateLogin` in `OAuth2Controller.java` (Generate PKCE, Store State, Return URL)
- [x] T013 [US1] Implement `handleCallback` in `OAuth2Controller.java` (Validate State, Exchange Code)
- [x] T014 [US1] Implement ID Token Validation logic (Placeholder or Jose) in `OAuth2Controller.java`
- [x] T015 [US1] Implement Session Creation (Set JSESSIONID) in `OAuth2Controller.java`
- [x] T016 [US1] Create Login Page/Button in `frontend/src/index.html`
- [x] T017 [US1] Connect Login Button to Backend `initiateLogin` in `frontend/src/js/app.js`

## Phase 4: User Story 2 - View User Profile (Priority: P2)

**Goal**: Display logged-in user's name and email.

**Independent Test**: Login -> Dashboard shows "Welcome, [Name]".

### Implementation for User Story 2

- [x] T018 [US2] Implement `getCurrentUser` endpoint in `OAuth2Controller.java`
- [x] T019 [US2] Create Dashboard UI elements in `frontend/src/index.html`
- [x] T020 [US2] Implement fetch user logic on page load in `frontend/src/js/app.js`
- [x] T021 [US2] Handle 401 Unauthorized (Redirect to Login) in `frontend/src/js/auth.js`

## Phase 5: User Story 3 - Token Management & Session Persistence (Priority: P2)

**Goal**: Visual countdown and manual refresh capability.

**Independent Test**: Watch timer -> Click Refresh -> Timer resets.

### Implementation for User Story 3

- [x] T022 [US3] Update `getCurrentUser` to return `tokenExpiry` in `OAuth2Controller.java`
- [x] T023 [US3] Implement `refreshToken` endpoint in `OAuth2Controller.java`
- [x] T024 [US3] Implement Countdown Timer logic in `frontend/src/js/app.js`
- [x] T025 [US3] Implement Refresh Button UI and logic in `frontend/src/js/app.js`

## Phase 6: User Story 4 - Logout (Priority: P3)

**Goal**: Secure session termination.

**Independent Test**: Click Logout -> Backend invalidates -> Frontend redirects to Login.

### Implementation for User Story 4

- [x] T026 [US4] Implement `logout` endpoint in `OAuth2Controller.java` (Revoke Google Token, Invalidate Session)
- [x] T027 [US4] Implement Logout UI and logic in `frontend/src/js/app.js`

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T028 [P] Update E2E Tests for full flow in `e2e-tests/`
- [x] T029 Update `quickstart.md` with final instructions
- [ ] T030 Final code cleanup and format check

## Dependencies & Execution Order

1.  **Setup & Foundation** matches Plan architecture.
2.  **US1** establishes the core Auth loop.
3.  **US2** validates Session persistence.
4.  **US3** adds Token management.
5.  **US4** adds Security cleanup.
