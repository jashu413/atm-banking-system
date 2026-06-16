# Migration Plan: Console ATM → Full Stack Banking Application

This document describes the plan to evolve the current in-memory Java 21 console ATM
into a production-grade full stack banking application.

**Target stack:** Java 21 · Spring Boot 3.x · Spring Security · JWT · Spring Data JPA ·
Hibernate · MySQL · Maven · Swagger/OpenAPI · JUnit 5 · Mockito · Docker · Redis (optional) · React

**Status:** Architecture finalized. No application code has been migrated yet.

---

## Finalized decisions

These choices are locked and drive the rest of this document:

1. **Authentication model — username/password + separate transaction PIN.**
   Login uses `username` + `password` (BCrypt) and issues a JWT. The 4-digit PIN
   (BCrypt-hashed) is a *secondary* credential required for `withdraw`, `transfer`,
   and `change-pin` — not for login.
2. **Schema management — Hibernate `ddl-auto`.**
   The database schema is derived from JPA entities (`ddl-auto=update` in dev), seeded
   via `data.sql` / a `CommandLineRunner`. No Flyway. See the caveat in
   [Risks and assumptions](#risks-and-assumptions).
3. **Redis — deferred to the final phase.** Phases 0–5 ship without it; token blacklist,
   rate-limiting, and caching are added in Phase 6 as optional enhancements.

---

## 1. Current architecture

A layered Java 21 console application built with Maven. Pure OOP + JDK collections, no
frameworks. State is entirely in memory and lost on exit.

```
ATM (controller / console UI)
 ├── AuthenticationService  ── login, 3-attempt lockout, admin check
 └── ATMService             ── balance, deposit, withdraw, transfer, history, PIN, seed data
        └── Customer *── BankAccount (abstract)
                            ├── SavingsAccount
                            └── CurrentAccount
                            └── *── Transaction → TransactionType (enum)
```

### Layers

- **Model** (`com.atm.model`): `BankAccount` is abstract and holds the real domain logic
  (deposit/withdraw/transfer/PIN/validation). `SavingsAccount` / `CurrentAccount` override
  only `getAccountType()`. `Transaction` is immutable. State (`pin`, `balance`, `locked`,
  `transactions`) is properly encapsulated.
- **Service** (`com.atm.service`): `AuthenticationService` owns the lockout policy (failed-attempt
  map kept outside the account). `ATMService` orchestrates operations and seeds demo data via
  `createDemoCustomers()`.
- **Exception** (`com.atm.exception`): `ATMException extends RuntimeException` with specific
  subtypes (`InsufficientFundsException`, `InvalidAmountException`, `InvalidPinException`,
  `WithdrawalLimitExceededException`, `AuthenticationException`).
- **UI** (`ATM`): console menu loop with robust input handling.

### Characteristics

- Money uses `BigDecimal` throughout (correct).
- Daily withdrawal limit tracked via `withdrawnToday` / `withdrawalDate` with auto-reset on date change.
- Concurrency handled with `synchronized` methods (single-threaded in practice).
- **No persistence** — all data is in-memory `HashMap` / `ArrayList`.

### Reuse audit

| Current class | Verdict | Notes |
|---|---|---|
| `BankAccount` + `Savings`/`Current` | Reuse logic, re-annotate | Becomes a JPA `@Entity` with single-table inheritance. |
| `Customer` | Reuse, extend | Split into `Customer` (profile) + `UserAccount` (auth). |
| `Transaction` (immutable) | Reuse as entity | Already shaped for an append-only ledger. |
| `TransactionType` (enum) | Reuse as-is | Persisted via `@Enumerated(STRING)`. |
| `ATMService` | Refactor → `AccountService` / `TransferService` | Core orchestration survives; seeder replaces `createDemoCustomers()`. |
| `AuthenticationService` | Replace | Login moves to Spring Security + JWT; lockout policy reused as DB columns. |
| Exception hierarchy | Reuse + `@ControllerAdvice` | Maps cleanly to HTTP status codes. |
| `ATM` (console UI) | Discard (keep as reference) | Replaced by REST controllers + React. |

**~70% of domain logic is directly reusable.** What must be rewritten: persistence wiring,
login/auth, the UI, and DTO ↔ entity mapping.

---

## 2. Target Spring Boot architecture

Monorepo with a Maven backend and a separate React frontend. Strict layering:
`controller → service → repository → domain`. Controllers speak DTOs only; entities never
cross the controller boundary; services own transactions and business rules.

```
banking-app/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/bank/
│       ├── BankingApplication.java
│       ├── config/          # SecurityConfig, OpenApiConfig, CorsConfig (RedisConfig in Phase 6)
│       ├── security/        # JwtTokenProvider, JwtAuthFilter, CustomUserDetailsService, entrypoint
│       ├── domain/          # JPA entities: UserAccount, Customer, BankAccount,
│       │                    #   SavingsAccount, CurrentAccount, Transaction; enums
│       ├── repository/      # UserRepository, AccountRepository, TransactionRepository, CustomerRepository
│       ├── service/         # AuthService, AccountService, TransferService, TransactionService, AdminService
│       ├── controller/      # AuthController, AccountController, TransactionController,
│       │                    #   TransferController, AdminController
│       ├── dto/             # request/response records (LoginRequest, TransferRequest, AccountResponse, ...)
│       ├── mapper/          # entity ↔ DTO mappers (MapStruct or manual)
│       ├── exception/       # ported exceptions + GlobalExceptionHandler (@ControllerAdvice)
│       └── util/            # PIN hashing, money helpers, account-number masking
│   └── src/test/java/com/bank/   # JUnit5 + Mockito (service), @WebMvcTest (controllers),
│                                 #   @DataJpaTest (repos), Testcontainers (integration)
├── frontend/                # React (Vite + TypeScript)
├── docker-compose.yml       # backend, mysql (redis + frontend added later)
├── Dockerfile               # backend, multi-stage
└── README.md
```

### Key architectural shifts

| Console concern | Spring Boot replacement |
|---|---|
| In-memory `HashMap` / `ArrayList` | JPA repositories + MySQL |
| PIN-only identity | Username/password (BCrypt) + JWT + roles (`ADMIN`/`CUSTOMER`); PIN as transaction credential |
| `synchronized` methods | `@Transactional` + optimistic locking (`@Version`), pessimistic lock on transfer |
| `withdrawnToday` / `withdrawalDate` counters | Derived `SUM` query over today's withdrawal transactions |
| `createDemoCustomers()` | `data.sql` / `CommandLineRunner` seeder |
| Console exception printing | `GlobalExceptionHandler` → JSON error envelope + HTTP codes |

---

## 3. Database design

MySQL, normalized, with an append-only transaction ledger. Schema is **derived from JPA
entities** via Hibernate `ddl-auto` (no hand-written migrations). The tables below are the
entity-design target.

```
users
  id                    BIGINT PK AUTO_INCREMENT
  username              VARCHAR(50)  UNIQUE NOT NULL
  password_hash         VARCHAR(100) NOT NULL          -- BCrypt
  role                  ENUM('ADMIN','CUSTOMER') NOT NULL
  enabled               BOOLEAN DEFAULT TRUE
  failed_login_attempts INT DEFAULT 0
  account_locked        BOOLEAN DEFAULT FALSE
  created_at, updated_at TIMESTAMP

customers
  id            BIGINT PK
  user_id       BIGINT FK → users(id) UNIQUE
  name          VARCHAR(100) NOT NULL
  email, phone  VARCHAR
  created_at    TIMESTAMP

accounts
  id                     BIGINT PK
  account_number         VARCHAR(20) UNIQUE NOT NULL
  customer_id            BIGINT FK → customers(id)
  account_type           ENUM('SAVINGS','CURRENT') NOT NULL   -- single-table inheritance discriminator
  pin_hash               VARCHAR(100) NOT NULL                -- BCrypt transaction PIN
  balance                DECIMAL(19,2) NOT NULL DEFAULT 0.00
  daily_withdrawal_limit DECIMAL(19,2) NOT NULL
  status                 ENUM('ACTIVE','LOCKED','CLOSED') DEFAULT 'ACTIVE'
  version                BIGINT                               -- @Version, optimistic locking
  created_at, updated_at TIMESTAMP

transactions
  id              BIGINT PK
  account_id      BIGINT FK → accounts(id)    -- indexed
  type            ENUM('DEPOSIT','WITHDRAWAL','TRANSFER_IN','TRANSFER_OUT','PIN_CHANGE')
  amount          DECIMAL(19,2) NOT NULL
  balance_after   DECIMAL(19,2) NOT NULL
  description     VARCHAR(255)
  related_account VARCHAR(20)                  -- counterparty for transfers
  created_at      TIMESTAMP                    -- indexed (mini-statement ordering)
```

### Design decisions

- **Single-table inheritance** for accounts (`account_type` discriminator) — Savings/Current
  differ only by type label today. Switch to joined strategy if they diverge.
- **Daily limit is computed, not stored:**
  `SELECT COALESCE(SUM(amount),0) FROM transactions WHERE account_id=? AND type='WITHDRAWAL' AND created_at >= CURDATE()`
  — no stale counters to reset.
- **`DECIMAL(19,2)`** for all money fields (mirrors current `BigDecimal`).
- **Optimistic locking** (`version`) guards against lost updates; `TransferService` uses a
  pessimistic `SELECT ... FOR UPDATE` for the debit+credit critical section.
- **Append-only ledger** — transactions are never updated or deleted.

---

## 4. REST API design

Base path `/api/v1`. JWT passed as `Authorization: Bearer <token>`. All endpoints documented
via Swagger/OpenAPI (`springdoc-openapi`).

### Auth (public)

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create customer + user |
| POST | `/auth/login` | Username/password → JWT (+ refresh token) |
| POST | `/auth/refresh` | Exchange refresh token |
| POST | `/auth/logout` | Invalidate token (Redis blacklist, Phase 6) |

### Account (CUSTOMER, own account)

| Method | Path | Purpose |
|---|---|---|
| GET | `/accounts/me` | Account details + balance |
| GET | `/accounts/me/balance` | Balance only |
| POST | `/accounts/me/deposit` | `{amount}` |
| POST | `/accounts/me/withdraw` | `{amount, pin}` |
| POST | `/accounts/me/change-pin` | `{oldPin, newPin}` |

### Transactions

| Method | Path | Purpose |
|---|---|---|
| GET | `/transactions` | Full history (paginated) |
| GET | `/transactions/mini-statement?count=5` | Recent N |

### Transfer

| Method | Path | Purpose |
|---|---|---|
| POST | `/transfers` | `{targetAccountNumber, amount, pin}` |

### Admin (ADMIN role)

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/accounts` | List all (paginated) |
| GET | `/admin/accounts/{id}` | Account details |
| POST | `/admin/accounts` | Create account |
| PATCH | `/admin/accounts/{id}/lock` · `/unlock` | Lock / unlock |
| PATCH | `/admin/accounts/{id}/reset-pin` | Reset PIN |
| GET | `/admin/customers` | List customers |

### Error handling

`GlobalExceptionHandler` returns a consistent JSON envelope
`{timestamp, status, error, message, path}` with mappings:

| Exception | HTTP status |
|---|---|
| `InvalidAmountException`, `InvalidPinException` | 400 Bad Request |
| `AuthenticationException` (bad credentials) | 401 Unauthorized |
| Forbidden (wrong role) | 403 Forbidden |
| Not found (account/customer) | 404 Not Found |
| Account locked | 423 Locked |
| `InsufficientFundsException`, `WithdrawalLimitExceededException` | 422 Unprocessable Entity |

---

## 5. Implementation phases

Each phase is independently testable.

### Phase 0 — Foundation
- `git init` + initial commit to preserve the working console app (history is currently empty).
- Scaffold Spring Boot 3.x (Web, Data JPA, Security, Validation, MySQL driver), Java 21.
- `docker-compose` with MySQL. Verify the app boots.

### Phase 1 — Domain & persistence
- Port entities (`BankAccount` hierarchy, `Customer`, `Transaction`, enums) with JPA annotations.
- Repositories + `ddl-auto` schema generation.
- Seeder replacing `createDemoCustomers()`.
- `@DataJpaTest` repository tests.

### Phase 2 — Core banking services
- Port `AccountService`, `TransferService`, `TransactionService` with `@Transactional`,
  optimistic/pessimistic locking, ported validation, and the daily-limit query.
- Adapt existing unit tests → Mockito service tests.

### Phase 3 — Security & JWT
- `SecurityConfig`, BCrypt, `UserDetailsService`, `JwtTokenProvider`, `JwtAuthFilter`, role authorization.
- Port lockout policy to DB columns. Auth endpoints + PIN as transaction credential.

### Phase 4 — REST API & docs
- Controllers + DTOs + mappers + `GlobalExceptionHandler`.
- Swagger/OpenAPI.
- `@WebMvcTest` controller tests; Testcontainers integration tests.

### Phase 5 — React frontend
- Vite + React + TypeScript, Axios with JWT interceptor, role-based protected routes.
- Pages: login, dashboard/balance, deposit/withdraw, transfer, history/mini-statement, admin console.

### Phase 6 — Hardening & delivery (Redis optional)
- Redis for token blacklist, rate-limiting, caching.
- Multi-stage Dockerfiles, full `docker-compose` (backend + mysql + redis + frontend).
- GitHub Actions CI (build / test / image).

---

## Risks and assumptions

### Risks

- **`ddl-auto=update` is not production-safe.** It never drops or safely alters columns and
  can silently diverge from intent. *Mitigation:* keep JPA entities as the single source of
  truth, use `ddl-auto=update` only in dev, set `validate` in higher environments, and treat a
  later switch to Flyway/Liquibase as a known follow-up.
- **Concurrent transfers / double-spend.** Two simultaneous withdrawals or transfers could
  race. *Mitigation:* `@Version` optimistic locking plus a pessimistic lock around the
  debit+credit critical section in `TransferService`, all inside one `@Transactional` boundary.
- **JWT secret & token handling.** Leaked secrets or non-expiring tokens are a security risk.
  *Mitigation:* externalize secrets (env vars), short-lived access tokens + refresh tokens,
  and a Redis blacklist for logout/revocation in Phase 6.
- **PIN/password storage.** Must never be stored in plaintext. *Mitigation:* BCrypt for both
  the login password and the transaction PIN.
- **Money precision.** Floating-point math would corrupt balances. *Mitigation:* `BigDecimal`
  in Java and `DECIMAL(19,2)` in MySQL end to end.
- **Local tooling gap.** Maven is not installed on the current machine (only Java 21).
  *Mitigation:* add the Maven Wrapper (`mvnw`) so builds do not depend on a global Maven install.
- **Scope creep.** The full stack target is large. *Mitigation:* strict phase boundaries;
  each phase must build and pass tests before the next begins.

### Assumptions

- Java 21 is the runtime for both the current app and the target backend.
- A single MySQL instance is sufficient; no sharding/replication is required initially.
- One customer maps to one bank account (the current model); multi-account-per-customer is a
  future extension the schema already tolerates.
- Savings vs. Current differ only by type label for now (justifying single-table inheritance).
- The console application is preserved as a reference and is not deleted during migration.
- Redis is optional and not required for core functionality through Phase 5.
- Demo/seed data remains acceptable for non-production environments.
