# Banking Application (Full Stack)

A banking application evolving from a Java 21 console ATM into a professional full stack
system (Spring Boot + React). The migration is planned and tracked in
[docs/migration-plan.md](docs/migration-plan.md).

## Repository layout

```
ATM/
├── docs/            # Architecture & migration plan
├── atm-console/     # Original working Java 21 console ATM (reference implementation)
├── backend/         # Spring Boot 3.x REST backend (in progress)
├── frontend/        # React frontend (planned — Phase 5)
└── docker-compose.yml
```

## Modules

| Module | Status | Description |
|---|---|---|
| [atm-console](atm-console/) | ✅ Complete | In-memory menu-driven console ATM. See its [README](atm-console/README.md). |
| [backend](backend/) | 🚧 Phase 2 (core services) | Spring Boot 3.x + Spring Security + JPA + MySQL REST API. |
| frontend | 📋 Planned | React + Vite + TypeScript SPA. |

## Target stack

Java 21 · Spring Boot 3.x · Spring Security · JWT · Spring Data JPA · Hibernate · MySQL ·
Maven · Swagger/OpenAPI · JUnit 5 · Mockito · Docker · Redis (optional) · React

## Getting started (backend, Phase 0)

A Maven install is **not** required — use the bundled Maven Wrapper.

```bash
# 1. Start MySQL
docker compose up -d

# 2. Build & run the backend tests (offline, uses in-memory H2)
cd backend
./mvnw test          # Windows: .\mvnw.cmd test

# 3. Run the application (requires MySQL from step 1)
./mvnw spring-boot:run
```

Once running:
- API base path: `http://localhost:8080/api/v1` (endpoints added in later phases)
- Swagger UI: `http://localhost:8080/swagger-ui.html`

> **Note:** `spring.jpa.hibernate.ddl-auto=update` is used for development only and is not
> production-safe. See the migration plan's *Risks and assumptions* section.

## Current state

Phases 0–2 are complete:

- **Phase 0 — Foundation:** backend scaffolded (package structure, dependencies, config,
  Docker Compose for MySQL, Maven Wrapper).
- **Phase 1 — Domain & persistence:** JPA entities (`BankAccount` hierarchy, `Customer`,
  `Transaction`), repositories, demo-data seeder, and `@DataJpaTest` persistence tests.
- **Phase 2 — Core banking services:** `AccountService` (balance, deposit, withdraw,
  change-PIN), `TransferService` (atomic debit+credit under a pessimistic lock, with
  deterministic lock ordering to avoid deadlock), and `TransactionService` (history,
  mini-statement) — all transactional, with Mockito unit tests.

**Not yet built:** REST controllers/DTOs, security & JWT (Phase 3), the REST API layer
(Phase 4), and the React frontend (Phase 5). See
[docs/migration-plan.md](docs/migration-plan.md) for the full roadmap.
