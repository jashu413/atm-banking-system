# Authentication Flow

## Token architecture

The system uses a dual-token JWT scheme:

| Token | Lifetime | Purpose |
|-------|----------|---------|
| Access token | 15 minutes | Bearer token on every API request |
| Refresh token | 7 days | Obtain a new access token without re-login |

Both tokens are stored in `sessionStorage` under the key `atm.auth.session`. They are cleared when the browser tab is closed.

## Login sequence

```mermaid
sequenceDiagram
    actor User
    participant Browser as React SPA
    participant API as Spring Boot API
    participant DB as MySQL

    User->>Browser: Enter username + password
    Browser->>API: POST /api/v1/auth/login
    API->>DB: SELECT user WHERE username = ?
    DB-->>API: UserAccount (hashed password)
    API->>API: BCrypt.verify(password, hash)
    alt Credentials valid
        API->>API: Generate access token (15 min)
        API->>API: Generate refresh token (7 days)
        API-->>Browser: 200 { accessToken, refreshToken, username, role }
        Browser->>Browser: Store tokens in sessionStorage
        Browser->>API: GET /api/v1/users/me (with access token)
        API-->>Browser: { username, role, customerName, accountNumber }
        Browser->>Browser: Auto-select account number (CUSTOMER role)
        Browser->>Browser: Navigate to /dashboard or /admin
    else Credentials invalid
        API-->>Browser: 401 Unauthorized
        Browser->>User: Show error message
    end
```

## Silent token refresh

The Axios client intercepts every 401 response (excluding auth endpoints) and attempts a silent refresh before surfacing the error to the page.

```mermaid
sequenceDiagram
    participant Page as React Page
    participant Axios as Axios Interceptor
    participant API as Spring Boot API

    Page->>Axios: api.getAccount(accountNumber)
    Axios->>API: GET /api/v1/accounts/:n (expired access token)
    API-->>Axios: 401 Unauthorized

    Axios->>API: POST /api/v1/auth/refresh { refreshToken }
    alt Refresh succeeds
        API-->>Axios: 200 { accessToken }
        Axios->>Axios: Update sessionStorage with new access token
        Axios->>API: Retry original request (new access token)
        API-->>Axios: 200 { account data }
        Axios-->>Page: Return account data (transparent to the page)
    else Refresh fails (token expired / revoked)
        Axios->>Axios: Clear sessionStorage
        Axios->>Browser: window.location.href = '/login'
    end
```

## Logout sequence

```mermaid
sequenceDiagram
    actor User
    participant Browser as React SPA
    participant API as Spring Boot API

    User->>Browser: Click "Sign out"
    Browser->>Browser: Clear sessionStorage (tokens + selected account)
    Browser->>API: POST /api/v1/auth/logout (best-effort)
    Note over API: Stateless JWT — no server-side session to invalidate
    Browser->>Browser: Navigate to /login
```

## Role-based access control

| Role | Accessible routes |
|------|-------------------|
| `CUSTOMER` | `/dashboard`, `/account`, `/deposit`, `/withdraw`, `/transfer`, `/pin`, `/transactions`, `/mini-statement` |
| `ADMIN` | `/admin`, `/admin/accounts`, `/admin/lock-unlock` |

The React router's `ProtectedRoute` component enforces roles on the client. Spring Security's `@PreAuthorize` and URL rules enforce them on the server independently.

## Password and PIN hashing

| Credential | Algorithm | Notes |
|------------|-----------|-------|
| Login password | BCrypt (strength 10) | Verified by `BCryptPasswordEncoder` in `AuthService` |
| Transaction PIN | BCrypt (strength 10) | Stored as `pin_hash`; verified in `AccountService` |

Passwords and PINs are never stored or transmitted in plain text.
