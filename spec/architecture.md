# Architecture

> Technology stack and application structure. `pom.xml` is the source of truth for versions. Do not modify `pom.xml`, `vite.config.ts`, or `architecture.md` without asking.

---

## 1. Technology Stack

- Vaadin — server-side Java UI for admin views, client-side React for public views
- Spring Boot — auto-configuration, embedded Tomcat
- Java
- Maven (wrapper included)
- Database: PostgreSQL in production; H2 in-memory for automated tests and local development (see section 5)
- Persistence: Spring Data JPA (Hibernate), schema managed by Flyway migrations
- Routing: Vaadin Flow views use `@Route`. Hilla React views use file-based routing (`src/main/frontend/views/`), not `src/main/frontend/routes.tsx`.
- Testing: JUnit 5, Vaadin Browserless Tests (`browserless-test-junit6`), Vitest for React views. Tests are organized per use case, not per view — see `/use-case-tests` for the convention.
- Browser tests: Playwright for Java (tag `e2e`, `./mvnw test -Pe2e`) verify layout, responsiveness and contrast in a real browser; see DEVELOPMENT.md.

---

## 2. Application Structure

```
com.stubu.specdriven/
  Application.java              — Spring Boot entry point
  [feature-package]/
    [FeatureView].java          — Vaadin @Route view
    [FeatureService].java       — Business logic (Spring @Service)
    [FeatureRepository].java    — Data access (Spring Data)
```

---

## 3. UIState Management

- **Signals** are the primary mechanism for managing UI state
- **Non-shared signals** for standard per-user UI state (e.g., form values, selection state, view-local data)
- **Shared signals** when state must be visible across multiple users/sessions (collaborative or real-time features) — requires **server push** to be enabled
- When using shared signals, enable push on the Application class (i.e. add a `@Push` annotation)

---

## 4. Security & Admin

- **Spring Security** with `VaadinSecurityConfigurer`
- Public routes and endpoints: `@AnonymousAllowed`
- Admin Flow routes: `@RolesAllowed("ADMIN")`
- Login: custom Vaadin login view at `/login` with one "Sign in with …" button per configured OIDC provider (Microsoft Entra ID, Google). Spring Security `oauth2Login` handles the OIDC flow; no username/password form.
- **IAM abstraction:** providers are configured under `stubu.iam.providers.<id>.*` (`google`, `microsoft` presets; any other id is a generic OIDC provider with explicit endpoints). Only providers with a client id appear on the login page. Credentials come from the environment, never from the repository. Redirect URI to register: `{base-url}/login/oauth2/code/{id}`.
- **Identity resolution:** the OIDC ID token supplies only the email (provider-agnostic `IamEmailExtractor`); the employee, and thus the roles, come from the application database. MANAGER and ADMIN employees are also granted the EMPLOYEE role. An employee is never created from a login.
- **Deny by default:** `VaadinSecurityConfigurer` denies every non-Vaadin request that is not explicitly opened, so new HTTP endpoints must declare their own access rules.
- **UI texts:** all user-facing strings live in `src/main/resources/vaadin-i18n/translations*.properties` (English default, German) and are applied in `LocaleChangeObserver.localeChange`, because a component's locale is only known once it is attached.

---

## 5. Persistence

| Environment | Database | Selected by |
|-------------|----------|-------------|
| Production | PostgreSQL | default configuration (no profile) |
| Automated tests | H2, in-memory | Spring profile `test` (`src/test/resources/application-test.properties`, activated with `@ActiveProfiles("test")`) |
| Local development | H2, in-memory | Spring profile `dev` (`./mvnw -Dspring-boot.run.profiles=dev`) |

- **Schema management:** Flyway, versioned migrations in `src/main/resources/db/migration` (`V1__…sql`, `V2__…sql`). Migrations are additive and must run unchanged on both H2 and PostgreSQL, so use portable SQL only — no vendor-specific types or functions.
- **Hibernate DDL:** `spring.jpa.hibernate.ddl-auto=validate` in every environment. Hibernate never creates or alters the schema, so tests and the `dev` profile exercise the same migrations as production.
- **H2 configuration:** in-memory URL with PostgreSQL compatibility mode, e.g. `jdbc:h2:mem:stubu;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`. The database exists only for the lifetime of the JVM; no data is persisted.
- **PostgreSQL configuration:** connection settings come from the environment (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). No credentials in the repository.
- **Tests:** use H2 only. Do not require a running PostgreSQL instance or Docker to run `./mvnw test`.
- **Dependencies** (managed by the Spring Boot BOM, Spring Boot 4 starter names): `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `com.h2database:h2` (runtime, used by the `dev` profile and tests), plus `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-client` and `spring-boot-starter-security-test` (test).
- **Config files:** `application.properties` (production/PostgreSQL), `application-dev.properties` (H2 plus sample employees from `db/dev`), and `src/test/resources/application-test.properties` (H2). Tests must not rely on the main file being shadowed.

---

## 6. Time Handling

- **Server clock:** every recorded timestamp comes from the injectable `java.time.Clock` bean (`ClockConfiguration`); the browser clock is never used. Tests replace the clock with a controllable one.
- **Storage:** work periods and audit records are absolute UTC instants (`TIMESTAMP WITH TIME ZONE` / `Instant`), so periods can cross midnight.
- **Presentation:** "today" and all displayed times use the browser's time zone (fetched with `retrieveExtendedClientDetails`), falling back to the server zone. Times and dates are formatted for the user's locale.
- **Invariants in the database:** at most one open work period per employee (unique `open_employee_id`), and a check-out never precedes its check-in (check constraint). Concurrent updates are detected with a `version` column.
- **Live refresh:** an open time tracking panel reloads itself every `stubu.time-tracking.refresh-interval` (default 30 seconds, `UI.triggerAfter`, no server push needed) and redraws only when an entry or the displayed minute changed.
- **Corrections:** a work period can only be corrected or deleted while the timesheet of its month (by the user's time zone) is a DRAFT; `TimesheetService.statusOf` treats a month without a timesheet record as a draft. Deleting is permanent; the audit entry keeps the old times and the reason.
- **Navigation:** `MainLayout` is an `AppLayout` with a drawer `SideNav` (Today, My Timesheet); the drawer is open on wide screens and toggled on narrow ones.
- **Monthly timesheet:** `MonthlyTimesheetService.load` returns one `DayLine` per calendar day (entries grouped by the user's local start day, weekend and public holiday flags) and creates the month's timesheet on first access; entries are locked when the status is not DRAFT. `MonthlyTimesheetView` renders it as a timeline (`MonthTimeline`) or a table, and reuses `EntryCorrectionDialogs` and `DayTrack` from the time tracking package.
- **Submission and resubmission:** `TimesheetSubmissionService.submit` (a draft, or a rejected timesheet after its correction, UC-008) applies `SubmissionRules` (draft, month over in the user's zone, at least one work period, none still open), sets SUBMITTED and the submission time, and writes the SUBMIT audit entry in one transaction; the `@Version` of the timesheet makes a concurrent second submission fail. Afterwards it calls `NotificationService.timesheetSubmitted`; `EmailNotificationService` sends the translated email through `JavaMailSender` when `spring.mail.host` is set and only logs otherwise (`stubu.notifications.from|locale|zone`). Delivery errors never undo the submission.
- **Review:** `TimesheetReviewService` lists (`pending`), opens (`details`), approves and rejects submitted timesheets for a reviewer. `ReviewerAuthorization` holds the rules (manager: direct reports and own department; administrator: may look at everybody's timesheet but never decides; nobody reviews their own; inactive users nobody), so they do not depend on any page. A decision and its audit entry (APPROVE/REJECT with reviewer and reason) are one transaction, the `@Version` of the timesheet makes a concurrent second decision fail, and the employee is notified afterwards through `NotificationService`. `ApprovalsView` (`/approvals`) and `TimesheetReviewView` (`/approvals/review/{id}`) are restricted to the MANAGER and ADMIN roles. A rejected timesheet stays REJECTED with its reason and its entries can be corrected again (`TimesheetStatus.allowsCorrections`).
- **Looking at employees:** `TimesheetReviewService.employees` lists who a reviewer may look at (sorted by name) and `employeeTimesheet` returns an employee's month in any status without creating a timesheet (`MonthlyTimesheetService.loadIfExists`), together with its history from the audit log. `EmployeesView` (`/employees`) and `EmployeeTimesheetView` (`/employees/timesheet/{id}`) are restricted to MANAGER and ADMIN. A message for the next page (e.g. "no permission") is passed with `FlashMessage`, which lives in the session until it is shown once.
- **Scope:** `TimesheetReviewService.scopeOptions` tells how many employees the direct-reports and department scopes cover and whether the department scope is available; `ScopeSwitcher` shows the choice on the Approvals and Employees pages and `ReviewScopePreference` keeps it in the session.
- **Employee management:** `EmployeeAdminService` (employee package) creates, updates and deactivates employees for an active administrator only, validates everything itself (email format and uniqueness, required fields, existing department and manager, no manager cycles, the last administrator stays) and stores the change with its audit entry in one transaction; `AuditJson` writes the old and new values. `EmployeeManagementView` (`/admin/employees`, ADMIN only) and `EmployeeFormDialog` are the page. Navigation: managers see Approvals and Employees, administrators see Employees (management).
