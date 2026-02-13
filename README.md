# OAuth2 Spring Nginx Demo

A demonstration of OAuth2 Authorization Code flow with PKCE using Google as the provider.
Built with Spring Boot 4.0.x (Backend) and static HTML/JS on Nginx (Frontend).

## Prerequisites

- **Podman** & **Podman Compose**
- Java 21 JDK (optional, for local development)
- Gradle 9.3.1+ (optional, wrapper included)

## Setup

1. **Environment Variables**:
   The project requires a `.env` file with your Google OAuth2 credentials.
   Copy `.env.example` and fill it in:

   ```bash
   cp .env.example .env
   ```

   ```env
   GOOGLE_CLIENT_ID=your-client-id
   GOOGLE_CLIENT_SECRET=your-client-secret
   ```

## Running the Application

Use `podman compose` to build and start the containers:

```bash
podman compose up --build
```

- **Frontend**: https://frontend.brunorozendo.dev
- **Backend**: https://backend.brunorozendo.dev (API)

## Features

- **Secure Login**: Sign in with Google Identity.
- **PKCE Flow**: Secure code exchange happening on the backend.
- **Session Management**: Server-side session with cookie-based auth.
- **User Profile**: View name and email from Google Profile.
- **Token Refresh**:
    - Automatic refresh 30 seconds before token expiry.
    - Manual "Refresh Now" button.
- **Countdown Timer**: Visual indicator of token expiration.
- **Secure Logout**: Invalidates local session.

## Testing

Run E2E tests (Selenium):

```bash
cd e2e-tests
./gradlew test
```
(Requires a Selenium Grid or local ChromeDriver)

## Project Structure

- `backend/`: Spring Boot 4.0.x application (Java 21).
- `frontend/`: Static file assets and Nginx config.
- `e2e-tests/`: Selenium WebDriver E2E tests (JUnit 5).
- `specs/`: Feature specifications and implementation plans.
- `docker-compose.yml`: Orchestration services.
