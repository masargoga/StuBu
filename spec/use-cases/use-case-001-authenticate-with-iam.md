# UC-001: Authenticate with IAM

> Copy this template for each feature as `use-case-NNN-short-name.md`.
> Replace all `[bracketed text]` with your content. Remove sections that genuinely do not apply (e.g. no secondary actor), but do not invent shortcuts — Preconditions, Trigger, Main Flow, and Postconditions are mandatory.

---

**Goal:** As a user, I want to log in using my enterprise IAM provider so that I can securely access the time tracking application.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** The login page is a card with the icon mark and one button per configured provider ("Sign in with Google", "Sign in with Microsoft", with the provider's logo); a provider without a client id is not offered. After login the session follows the employee record: when the employee is deactivated, deleted or given another role, the session ends within a few seconds (`stubu.security.recheck-interval`, default 5 seconds) and the user is sent to the login page (AF-5, BR-06). The login page, its stylesheet and the icon mark can be opened without signing in (`/login`, `/styles.css`, `/icons/**`). The `lang` attribute of the page is the language of the interface (English or German, following the browser). Health probes for Docker and Kubernetes (`/actuator/health/liveness`, `/actuator/health/readiness`) are the only other pages that need no login.

---

## Actors

- **Primary actor:** Unauthenticated user (Employee, Manager, or Administrator)
- **Secondary actors:** OIDC-compatible IAM system (Microsoft Entra ID or Google Login)

---

## Preconditions

- User is not yet authenticated
- User has an active employee record in the application database with an email matching their IAM identity
- IAM system (Entra ID or Google) is configured and reachable
- Application is configured to connect to the IAM provider

---

## Trigger

User navigates to the application URL and is redirected to the login page.

---

## Main Flow

1. User arrives at the application login page.
2. System displays login options (e.g., "Sign in with Microsoft" or "Sign in with Google").
3. User clicks a login option.
4. System redirects user to the IAM provider's authentication page (OIDC/OAuth 2.0 flow).
5. User authenticates with their IAM credentials (username, password, MFA, etc.).
6. IAM provider redirects user back to the application with an authorization code.
7. System exchanges the authorization code for tokens (ID token, access token).
8. System extracts the authenticated user's email from the ID token.
9. System queries the Employee table for an active employee matching that email.
10. System establishes the user context with the employee's roles (EMPLOYEE, MANAGER, ADMIN).
11. System redirects the user to their home dashboard.

---

## Alternative Flows

### AF-1: Employee Not Found

**Branches from:** Main Flow step 9
**Condition:** No active employee record exists for the authenticated email address

1. System displays an error message: "Your account is not registered in the time tracking system. Please contact your administrator."
2. System clears the session and redirects to login page.
3. Use case ends.

### AF-2: Employee Account Inactive

**Branches from:** Main Flow step 9
**Condition:** An employee record exists for the email but `isActive` is false

1. System displays an error message: "Your account has been deactivated. Please contact your administrator."
2. System clears the session and redirects to login page.
3. Use case ends.

### AF-3: IAM Provider Error

**Branches from:** Main Flow step 4–6
**Condition:** IAM provider returns an error or is unreachable

1. System displays an error message: "Authentication service is unavailable. Please try again later."
2. User is redirected to login page.
3. Use case ends.

### AF-4: Token Exchange Failure

**Branches from:** Main Flow step 7
**Condition:** Authorization code is invalid, expired, or token exchange fails

1. System displays an error message: "Authentication failed. Please try again."
2. System redirects to login page.
3. Use case ends.

### AF-5: Employee Deactivated or Role Changed While Signed In

**Branches from:** any request made after the login has completed
**Condition:** An administrator deactivates the employee, removes the employee, or changes the employee's role while they are signed in

1. At the latest after the re-check interval (default 5 seconds) the system finds that the employee is no longer active or has another role.
2. System ends the session.
3. The user's next request is redirected to the login page; after signing in again they have the rights of their current role (or are refused if inactive, see AF-2).
4. Use case ends.

---

## Postconditions

- **On success:** 
  - User is authenticated and has an active session
  - User context contains employee ID, email, and roles (EMPLOYEE, MANAGER, ADMIN)
  - User is redirected to their home dashboard
  - An audit log entry is created for successful login

- **On failure:** 
  - No session is established
  - User is redirected to login page
  - An audit log entry may be created for failed login attempts

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only OIDC/OAuth 2.0-compatible providers are supported |
| BR-02 | Email extraction must be provider-agnostic and abstracted from domain logic |
| BR-03 | Only active employees (isActive = true) are granted access |
| BR-04 | Session tokens must be securely stored and validated on each request |
| BR-05 | Failed authentication attempts should be logged for security audit |
| BR-06 | A session follows the employee record: an inactive or removed employee, or one with a changed role, is signed out within the re-check interval, so rights never outlive the record |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1–11)
- [x] AF-1 (Employee Not Found) covered
- [x] AF-2 (Employee Inactive) covered
- [x] AF-3 (IAM Provider Error) covered
- [x] AF-4 (Token Exchange Failure) covered
- [x] BR-01, BR-02, BR-03, BR-04, BR-05 covered

---

## UI Surface

> What the user sees and where they reach it. Keep this implementation-agnostic — no framework annotations, component class names, or file paths. The harness picks how to render it.

- **Login Page:** Displayed before authentication. Shows login options for supported IAM providers (Microsoft Entra ID, Google). User can click one to initiate login flow.
- **Error Messages:** Clear, user-friendly error messages for account not found, account inactive, and authentication failures.
- **Home Dashboard:** Displayed after successful authentication. Content varies by role (Employee, Manager, Admin).

| Page | Access |
|------|--------|
| Login | Anonymous (unauthenticated) |
| Home Dashboard | Authenticated (all roles) |
