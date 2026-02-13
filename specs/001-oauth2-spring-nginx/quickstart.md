# Quickstart: OAuth2 Implementation

## Prerequisites
- Podman (and `podman compose` plugin)
- Java 21 (for IDE support)

## Configuration
Ensure `.env` exists in root with:
```env
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

## Running the Application

1. **Build and Run**:
   ```bash
   podman compose up --build
   ```

2. **Access**:
   - Frontend: `https://frontend.brunorozendo.dev`
   - Backend API: `https://backend.brunorozendo.dev/auth`

## Testing
- **E2E Tests**:
  ```bash
  cd e2e-tests
  ./gradlew test
  ```
