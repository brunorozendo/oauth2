# Project Constitution: OAuth2 Spec-Kit

This document outlines the architectural principles, technology stack, and development standards for the OAuth2 implementation project. It serves as the single source of truth for technical decisions and constraints.

## 1. Core Principles

1.  **Modern Stack**: Prioritize the latest stable releases (Spring Boot 4, Java 21) to ensure longevity and performance.
2.  **Container-First**: All components must be containerized and orchestratable via `podman compose`. Development and production environments should mirror each other as closely as possible.
3.  **Separation of Concerns**: Strict boundary between Frontend (Static Assets/Nginx) and Backend (API/Java). No server-side rendering (Thymeleaf/JSP).
4.  **Security by Design**: Implement industry-standard security flows (OAuth2 + PKCE) from day one. Do not compromise security for "dev convenience" (e.g., proper state validation, secure cookies).
5.  **Statelessness**: The backend should remain as stateless as possible. Session data is stored via keys (e.g., OAuth `state`) that do not rely strictly on sticky sessions or fragile in-memory maps keyed by session IDs, especially during redirect flows.

## 2. Technology Stack

### Backend
-   **Framework**: Spring Boot 4.0.x (Jakarta EE 10 baseline).
-   **Language**: Java 21 (LTS).
-   **Build Tool**: Gradle 9.3.1+ (Groovy DSL).
-   **Security**: Spring Security 7.x.
-   **Database/Storage**: In-memory `ConcurrentHashMap` for MVP sessions (extensible to Redis).

### Frontend
-   **Tech**: Vanilla HTML5, CSS3, JavaScript (ES6+).
-   **Server**: Nginx (Alpine-based).
-   **Communication**: Fetch API with `credentials: 'include'`.

### Infrastructure & DevOps
-   **Runtime**: Docker / Podman.
-   **Command**: Use `podman compose` (space), NOT `podman-compose` (dash).
-   **Base Images**:
    -   Backend: `eclipse-temurin:21-jre-alpine`.
    -   Frontend: `nginx:alpine`.
-   **Orchestration**: `docker-compose.yml` (v3 schema, though version field is deprecated).

### Testing
-   **Unit**: JUnit 5, Mockito.
-   **E2E**: Selenium WebDriver (Java), running in containerized `seleniarm/standalone-chromium` (ARM) or `selenium/standalone-chromium` (x86).
-   **Browser Compatibility**: Chrome/Chromium (Headless).

## 3. Architecture & Patterns

### 3.1. Authentication Flow
-   **Protocol**: OAuth 2.0 Authorization Code Flow with PKCE.
-   **State Management**:
    -   PKCE `code_verifier` is stored temporarily, keyed by the `state` parameter generated for the request.
    -   `state` verification is mandatory in the callback.
-   **Session**:
    -   Post-login user session is managed via `JSESSIONID` cookies.
    -   Cookies must be `HttpOnly`.

### 3.2. Backend APIs
-   **Prefix**: All API endpoints must be prefixed with `/auth` (e.g., `/auth/login`, `/auth/callback`).
-   **CORS**: Must be explicitly configured to allow the Frontend origin (`https://frontend.brunorozendo.dev` or `.com` variants as configured).
-   **CSRF**: Disabled for MVP/Stateless APIs where appropriate (or token-based if scaled).

## 4. Operational Standards

### 4.1. Network & Domains
-   **Local Development**: `localhost` ports 9103 (Backend), 8631 (Frontend).
-   **Tunnel/Remote**: Use consistent TLDs for Frontend and Backend to avoid cookie issues.
    -   Current Standard: `*.brunorozendo.dev`.
-   **Troubleshooting**:
    -   Browser Caching: Changes to static JS files require cache busting (e.g., `?v=2`).
    -   DNS: Containerized tests cannot resolve host-local tunnels (e.g., Cloudflare Tunnels) without extra configuration. Manual verification is preferred for tunnel URLs.

### 4.2. Build & Deploy
-   **Backend Build**: `gradle bootJar` (creates uber-jar).
    -   *Do not* Use WAR deployment or external Tomcat containers. Use embedded Tomcat.
-   **Frontend Build**: Copy `src` to Nginx html root.
-   **Restart**: `podman compose build --no-cache && podman compose up -d` to ensure clean state.

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
-   **Commits**: Conventional Commits (e.g., `feat:`, `fix:`, `docs:`).
-   **Artifacts**: Keep `walkthrough.md`, `implementation_plan.md`, and `tasks.md` updated with progress.
