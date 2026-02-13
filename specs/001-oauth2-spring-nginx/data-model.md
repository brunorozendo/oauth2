# Data Model: OAuth2 Implementation

## Entities

### UserSession

Server-side representation of an authenticated user. Stored in `ConcurrentHashMap` (Mock Session Store).

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | String (UUID) | Primary Key. Maps to `JSESSIONID` cookie value. |
| `email` | String | User's email from Google ID Token. |
| `name` | String | User's display name. |
| `picture` | String | URL to user's avatar. |
| `accessToken` | String | Google OAuth2 Access Token. |
| `refreshToken` | String | Google OAuth2 Refresh Token (if granted). |
| `tokenExpiry` | Long (Timestamp) | Absolute expiry time (Epoch ms). |

### PKCEContext

Temporary state verification object. Stored in `ConcurrentHashMap` (Mock PKCE Store).

| Field | Type | Description |
|-------|------|-------------|
| `state` | String (UUID) | Primary Key. The unique state param sent to Google. |
| `codeVerifier`| String | The implementation-specific secret verifier. |
| `nonce` | String | Random nonce for ID Token validation. |
| `createdAt` | Long (Timestamp)| Creation time for cleanup/TTL. |
