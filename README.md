# StuBu – Employee Time Tracking

StuBu records working time. Employees check in and out (or correct their days afterwards), submit a monthly
timesheet, and their manager approves or rejects it. Administrators manage employees and public holidays and can
look at everything, including the audit log. The application is meant for companies in Germany and is available in
English, German, Spanish and French; everybody chooses their language in the language selector.

It is built specification-first: the requirements in [`spec/`](spec/) are the single source of truth, and each use
case was implemented and verified (automated tests plus a real-browser check at desktop, tablet and phone size) one
after the other.

## What it does

| Who | What |
|-----|------|
| **Everyone** | Sign in with Microsoft Entra ID or Google. Check in and check out, see today's timeline with worked and break time, correct earlier days, see the month with weekends and public holidays, submit the month for approval, correct and resubmit a rejected month. |
| **Managers** | Get an email when a timesheet is submitted. Approve or reject (with a reason) the timesheets of their direct reports and their department, look at any employee of that scope and their history. |
| **Administrators** | Add, change and deactivate employees. Maintain public holidays. Look at all employees and timesheets (but not approve them). Search and export the audit log. |

More: [end-user guide](docs/user-guide.md), [use cases](spec/use-cases/), [full specification](spec/spec.md).

## Quick start

Java 25 is required.

```bash
./mvnw spring-boot:test-run
```

Open <http://localhost:8080> and sign in through the mock identity provider with one of the sample employees:
`alice.employee@example.com`, `bob.manager@example.com`, `carol.admin@example.com` (or the deactivated
`dave.inactive@example.com`). Data lives in H2 in memory and is gone on restart.

```bash
./mvnw -Dvaadin.skip=true test     # fast tests (no browser), about 290 tests, 6 of them need Docker and are skipped without it
./mvnw test -Pe2e                  # real-browser tests with Playwright (needs Chrome or Edge), about 136 tests
```

## Documentation

| Document | Content |
|----------|---------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Build, run and test commands; every configuration setting; PostgreSQL, sign-in providers, email |
| [docs/operations.md](docs/operations.md) | Docker, Kubernetes, health checks, sessions, logging, retention, scaling |
| [docs/authorization.md](docs/authorization.md) | Who may do what, where it is enforced |
| [docs/domain.md](docs/domain.md) | Domain model, timesheet state machine, invariants, database schema |
| [docs/assumptions-and-legal.md](docs/assumptions-and-legal.md) | Assumptions made, and rules that legal or business stakeholders must confirm |
| [docs/user-guide.md](docs/user-guide.md) | The workflows from the point of view of employees, managers and administrators |
| [spec/architecture.md](spec/architecture.md) | Technology and structure, package by package |
| [spec/design-system.md](spec/design-system.md) | Look and feel, accessibility rules |

## Technology

Java 25, Spring Boot 4, Vaadin 25 (Flow, Aura theme), Spring Security with OpenID Connect, Spring Data JPA with
Flyway, PostgreSQL in production and H2 for tests and local development, JUnit 5, Vaadin browserless tests and
Playwright for Java.
