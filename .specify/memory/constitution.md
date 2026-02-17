<!--
  Sync Impact Report
  ===================
  Version change: 1.0.0 → 1.1.0 (MINOR — new architectural rules added)

  Modified sections:
  - §3.1 Authentication Flow: Added mandatory session persistence rule
  - §3.2 Backend APIs: Expanded CORS and CSRF rules with rationale

  Added sections:
  - §3.3 Spring Security Guardrails (new — 4 rules from failed implementation runs)
  - §4.3 ArchUnit Exceptions (new — known required exceptions)

  Removed sections: None

  Templates requiring updates:
  - .specify/templates/plan-template.md: ✅ No changes needed (Constitution Check is generic)
  - .specify/templates/spec-template.md: ✅ No changes needed (behavioral, not architectural)
  - .specify/templates/tasks-template.md: ✅ No changes needed (task structure unchanged)
  - .specify/templates/checklist-template.md: ✅ No changes needed (generic template)

  Follow-up TODOs: None
-->

# Project Constitution

This document is the single source of truth for architectural principles,
technology stack, and development standards. All spec-kit phases
(specify, plan, tasks, implement) MUST comply with this constitution.

## 1. Core Principles

1. **Modern Stack**: Prioritize the latest stable releases (Spring Boot 4,
   Java 21) to ensure longevity and performance.
2. **Container-First**: All components MUST be containerized and
   orchestratable via `podman compose`. Development and production
   environments MUST mirror each other as closely as possible.
3. **Separation of Concerns**: Strict boundary between Frontend (Static
   Assets/Nginx) and Backend (API/Java). No server-side rendering
   (Thymeleaf/JSP).
4. **Security by Design**: Implement industry-standard security flows
   from day one. Do not compromise security for
   "dev convenience" (e.g., proper state validation, secure cookies).
5. **Statelessness**: The backend MUST remain as stateless as possible.
   Session data is stored via keys that do not
   rely on sticky sessions or fragile in-memory maps keyed by session
   IDs, especially during redirect flows.

## 2. Technology Stack

### Backend

- **Framework**: Spring Boot 4.0.2 (Jakarta EE 10 baseline).
- **Language**: Java 21 (LTS).
- **Build Tool**: Gradle 9.3.1 (Groovy DSL), use wrapper.
- **Security**: Spring Security 7.x.
- **Database/Storage**: In-memory `H2` for MVP sessions (extensible
  to Redis).
- **Session Cookies**: MUST be configured with `HttpOnly`, `Secure`,
  and `SameSite=Lax`. Use Tomcat's native `HttpSession` (do **not**
  set `spring.session.store-type: none`).

### Frontend

- **Tech**: Vanilla HTML5, CSS3, JavaScript (ES6+).
- **Server**: Nginx (Alpine-based).
- **Communication**: Fetch API with `credentials: 'include'`.

### Infrastructure & DevOps

- **Runtime**: Docker / Podman.
- **Command**: Use `podman compose` (space), NOT `podman-compose` (dash).
- **Base Images**:
  - Backend: `eclipse-temurin:21-jdk-alpine` (build) →
    `alpine:latest` (runtime, with JLink-optimized JDK).
    [Dockerfile](Dockerfile_example_java)
  - Frontend: `nginx:alpine`.
- **Orchestration**: `docker-compose.yml` (v3 schema, though version
  field is deprecated).

### Testing

- **Unit**: Spock 2.4-groovy-5.0 + Groovy 5.0.3. Use plain Spock
  mocking (`Mock()`, `Stub()`) — do **not** use `@WebMvcTest`
  (removed in Spring Boot 4); use template [spock.gradle](spock.gradle).
- **Architecture**: ArchUnit (`com.tngtech.archunit:archunit:1.3.0`)
  for automated architecture verification. All tests MUST pass before
  completion.
  - Enforce: Layer isolation (Controller → Service → Repository).
  - Enforce: Naming conventions (Classes ending in Controller,
    Service, etc.).
  - Enforce: Annotation usage (e.g., `@RestController` on controllers).
  - Enforce: Crypto/Security usage rules where possible.
- **E2E**: Selenium WebDriver (Java) via Testcontainers
  `BrowserWebDriverContainer` using `seleniarm/standalone-chromium`
  (ARM) or `selenium/standalone-chromium` (x86).
- **Browser Compatibility**: Chrome/Chromium (Headless).

## 3. Architecture & Patterns

### 3.1. Backend APIs

- **Prefix**: All API endpoints MUST be prefixed with `/api`
  (e.g., `/api/auth/login`, `/api/auth/callback`).
- **CORS**: MUST be explicitly configured. The allowed origin MUST be
  **configurable** via `application.yml` (e.g., `app.frontend.origin`)
  rather than hardcoded. See §3.2 for mandatory placement rules.

### 3.2. Spring Security Guardrails

These rules address specific Spring Security pitfalls discovered during
implementation. Violating any of these will cause silent auth failures,
403 errors, or broken redirect flows.

1. **CORS in SecurityFilterChain**: CORS MUST be configured directly
   in the `SecurityFilterChain` via
   `.cors(cors -> cors.configurationSource(...))`, NOT only in
   `WebMvcConfigurer`. Spring Security intercepts requests before
   MVC — a MVC-only CORS config will cause 403 on preflight requests.
   The configuration MUST allow credentials (`AllowCredentials(true)`)
   and the specific frontend origin.

2. **Reverse Proxy Headers**: `application.yml` MUST include
   `server.forward-headers-strategy: framework`. Without this, Spring
   generates `http://` redirect URIs instead of `https://` when behind
   a TLS-terminating reverse proxy, breaking redirect URLs.

3. **Session Security Context Save**:
   After a successful authentication, the `UsernamePasswordAuthenticationToken` MUST be both:
   1. Set in `SecurityContextHolder`
      (`SecurityContextHolder.getContext().setAuthentication(...)`)
   2. Explicitly saved to the `HttpSession` under
      `HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY`
   Without **both** steps, subsequent authenticated requests will
   fail silently — the session exists but Spring Security will not
   recognize it as authenticated.

## 4. Operational Standards

### 4.1. Network & Domains

- **Local Development**: `localhost` ports 9103 (Backend),
  8631 (Frontend).
- **Tunnel/Remote**: Use consistent TLDs for Frontend and Backend to
  avoid cookie issues.
  - Current Standard: `*.brunorozendo.dev`.
- **Troubleshooting**:
  - Browser Caching: Changes to static JS files require cache busting
    (e.g., `?v=2` or distinct filenames).
  - DNS: Containerized tests cannot resolve host-local tunnels
    (e.g., Cloudflare Tunnels) without extra configuration. Manual
    verification is preferred for tunnel URLs.

### 4.2. Build & Deploy

- **Backend Build**: `gradle bootJar` (creates uber-jar). In
  Dockerfiles, use `./gradlew bootJar` — do **not** use
  `./gradlew build -x test`.
  - Do *not* use WAR deployment or external Tomcat containers. Use
    embedded Tomcat.
- **Frontend Build**: Copy `src` to Nginx html root.
- **Restart**: `podman compose build --no-cache && podman compose up -d`
  to ensure clean state.

### 4.3. ArchUnit Exceptions

Known exceptions that MUST be configured in ArchUnit rules to prevent
false-positive architecture test failures:

- **`org.slf4j` in Controller layer**: Controllers require logging for
  error diagnostics. ArchUnit layer rules MUST allow `org.slf4j`
  access in the Controller layer.

Additional exceptions MUST be documented here before being added to
the codebase.

## 5. Directory Structure

```text
/
├── backend/            # Spring Boot Application
│   ├── src/
│   ├── build.gradle
│   └── Dockerfile
├── frontend/           # Static Web Assets
│   ├── src/
│   ├── nginx.conf
│   └── Dockerfile
├── e2e-tests/          # Selenium Test Suite
│   ├── src/
│   └── build.gradle
├── specs/              # Documentation & Plans
├── .specify/           # Spec-Kit configuration & memory
│   └── memory/
│       └── constitution.md  # This file
└── docker-compose.yml  # Orchestration
```

## 6. Version Control & Contribution

- **Commits**: Conventional Commits (e.g., `feat:`, `fix:`, `docs:`).
- **Artifacts**: Keep `walkthrough.md`, `implementation_plan.md`, and
  `tasks.md` updated with progress.

## Governance

- This constitution supersedes all other practices and ad-hoc decisions.
- All implementation phases (specify, plan, tasks, implement) MUST
  verify compliance with this document.
- Amendments require: (1) documented rationale, (2) version bump per
  semver rules, (3) sync impact report.
- Versioning policy:
  - MAJOR: Backward-incompatible principle removals or redefinitions.
  - MINOR: New principle/section added or materially expanded guidance.
  - PATCH: Clarifications, wording, typo fixes, non-semantic changes.

**Version**: 1.1.0 | **Ratified**: 2026-02-15 | **Last Amended**: 2026-02-15
