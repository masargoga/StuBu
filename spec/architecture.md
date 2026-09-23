# Architecture

> Technology stack and application structure. `pom.xml` is the source of truth for versions. Do not modify `pom.xml`, `vite.config.ts`, or `architecture.md` without asking.

---

## 1. Technology Stack

- Vaadin — server-side Java UI for admin views, client-side React for public views
- Spring Boot — auto-configuration, embedded Tomcat
- Java
- Maven (wrapper included)
- Database: [e.g., PostgreSQL, H2]
- Routing: Vaadin Flow views use `@Route`. Hilla React views use file-based routing (`src/main/frontend/views/`), not `src/main/frontend/routes.tsx`.
- Testing: JUnit 5, Vaadin Browserless Tests (`browserless-test-junit6`), Vitest for React views. Tests are organized per use case, not per view — see `/use-case-tests` for the convention.

---

## 2. Application Structure

```
com.example.specdriven/
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
- Login: Vaadin `LoginForm` at `/login`
