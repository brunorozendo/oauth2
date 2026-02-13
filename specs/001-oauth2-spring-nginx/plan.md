# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

## Summary

Implement a full-stack OAuth2 authentication flow using Google as the provider, adhering to the project constitution. The backend will be built with **Spring Boot 4.0.x** (Java 21) using **Gradle**. The frontend will be a static SPA served by **Nginx**, consuming backend APIs.

## Technical Context

**Language/Version**: Java 21 (LTS), JavaScript (ES6+)
**Primary Dependencies**: Spring Boot 4.0.x, Spring Security 7.x, Gradle 9.3.1+, Nginx
**Architecture**: Manual PKCE Flow (Controller-based) vs Spring Auto-Config
**Storage**: In-memory `ConcurrentHashMap` for MVP ( Session/PKCE )
**Testing**: JUnit 5, Mockito, Selenium WebDriver (Containerized)
**Target Platform**: Podman Containers (Alpine Linux base)
**Project Type**: Web Application (Split Backend/Frontend)
**Performance Goals**: <500ms API response, <5s full login flow
**Constraints**: **Strict Domain Usage**: `https://backend.brunorozendo.dev` (Backend), `https://frontend.brunorozendo.dev` (Frontend). do NOT use `localhost`. **Cloudflare Tunnel** active.
**Scale/Scope**: MVP, single instance

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Modern Stack**: Uses Spring Boot 4 / Java 21 as mandated.
- [x] **Container-First**: Architecture relies on Podman Compose.
- [x] **Separation of Concerns**: Nginx for static assets, Java for API.
- [x] **Security**: Implements OAuth2 + PKCE with `HttpOnly` cookies.
- [x] **Statelessness**: Session store is abstractable (Map interface).

## Project Structure

### Documentation (this feature)

```text
specs/001-oauth2-spring-nginx/
├── plan.md              # This file
├── research.md          # Architectural decisions
├── data-model.md        # Session & Entity definitions
├── quickstart.md        # Run instructions
├── contracts/           # API Signatures
└── tasks.md             # Implementation Tasks
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/bunorozendo/auth/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── model/
│   │   │   ├── service/
│   │   │   └── Oauth2Application.java
│   │   └── resources/
│   └── test/
├── build.gradle
└── Dockerfile

frontend/
├── src/
│   ├── index.html
│   ├── js/
│   └── css/
├── nginx.conf
└── Dockerfile

e2e-tests/
├── src/
├── build.gradle
└── Dockerfile

docker-compose.yml
```

**Structure Decision**: Adheres to Constitution Section 5 (Directory Structure).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | | |
