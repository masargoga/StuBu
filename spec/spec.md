# AI Spec-Driven Development Master Prompt

## Enterprise Employee Time Tracking Application

You are an expert software architect, Java/Spring Boot developer, Vaadin developer, UX engineer, database designer, security engineer, and test engineer.

Your task is to design and implement a production-ready enterprise employee time-tracking application using **AI Spec-Driven Development**.

Do not immediately start writing implementation code.

First analyze the requirements, identify ambiguities and contradictions, establish the domain model and state transitions, define the architecture and authorization model, and produce an implementation plan. Only then implement the application incrementally.

The application must be maintainable, secure, testable, accessible, responsive, and suitable for deployment as a containerized application in Kubernetes.

---

# 1. Product Overview

Build an enterprise web application for recording employee working time.

The application allows employees to:

* authenticate using an external IAM provider;
* record check-in and check-out times;
* record multiple working periods per day;
* view the current day's working-time timeline;
* see total worked hours for the day;
* correct historical time records;
* review their current calendar-month timesheet;
* submit a completed monthly timesheet for manager approval;
* see whether their timesheet is submitted, approved, or rejected;
* correct rejected timesheets and resubmit them.

Managers can:

* see pending approval requests from their employees;
* view the complete timesheet and timeline of an employee;
* approve or reject a monthly timesheet;
* provide a rejection reason;
* view previously approved timesheets;
* switch between direct employees and all employees in their department.

Administrators can:

* manage employees;
* configure public holidays;
* view audit logs;
* access all employee and timesheet data.

The system must maintain an immutable audit trail for relevant changes.

---

# 2. Technology Requirements

Use the following technology stack unless a documented architectural reason requires otherwise:

* Java 21
* Spring Boot
* Vaadin
* Current stable Vaadin version compatible with Java 21 and the selected Spring Boot version
* Vaadin server-side application architecture
* H2 for development/testing
* PostgreSQL for production
* Spring Security
* OIDC/OAuth 2.0-compatible authentication abstraction
* Docker
* Kubernetes
* JPA/Hibernate or the current appropriate persistence technology for Spring Boot/Vaadin

The application must be capable of running with multiple Kubernetes pods.

The application must therefore be designed as a horizontally scalable application.

Do not rely on:

* in-memory application state as the source of truth;
* local filesystem persistence;
* a single application instance;
* static mutable state;
* pod-local state for business data.

All persistent business state must be stored in PostgreSQL in production.

---

# 3. Authentication

Authentication is delegated to an external IAM system.

The application must provide an IAM abstraction that allows different OIDC-compatible providers to be configured.

The initial intended providers are:

* Microsoft Entra ID
* Google Login

Do not tightly couple the domain/application logic to either provider.

Conceptually:

```
Vaadin Application
      |
      | OIDC / OAuth 2.0
      |
   IAM abstraction
      |
   +--+----------+
   |             |
Entra ID       Google
```

The IAM system provides the authenticated user's email address.

The application database provides all other employee information.

After successful authentication:

1. Obtain the authenticated email address.
2. Find the corresponding employee in the application database.
3. Establish the application user context and roles.
4. If no active employee exists for the authenticated email address, deny application access and display a clear user-facing message explaining that the authenticated account is not registered.

Do not create an employee automatically based solely on successful IAM authentication.

---

# 4. Roles

The application has exactly these primary application roles:

* EMPLOYEE
* MANAGER
* ADMIN

A user may have more than one role if appropriate.

Authorization must be enforced server-side.

Do not rely on UI visibility alone for security.

Every protected operation must validate authorization on the server.

---

# 5. Employee and Organization Model

Employee information is maintained by the application database.

At minimum, an employee contains:

* unique employee ID
* name
* email address
* department
* manager
* application roles
* active/inactive state

The organization hierarchy is represented using:

```
employee.managerId
```

A manager also has an employee record and therefore can have their own manager.

The hierarchy can therefore recursively continue toward the top of the organization.

Do not introduce a separate organizational hierarchy model unless required by implementation.

Departments are represented through employee department membership.

An employee has at most one manager.

---

# 6. Manager Authorization

A manager must be able to switch between:

## My direct employees

Employees where:

```
employee.managerId == currentManager.id
```

## My department

All employees belonging to the same department as the current manager.

Managers are permitted to approve timesheets for employees in their department, including employees who are not their direct reports.

Managers must not be able to access unrelated departments unless they have an ADMIN role.

The authorization rules must be implemented independently of the UI.

---

# 7. Time Recording Domain

An employee may have multiple working periods on a day.

Example:

```
08:00 ───────── 12:00
13:00 ───────── 17:00
```

This means:

* Worked time = 8 hours
* Break time = 1 hour

A work period consists of:

* check-in timestamp
* check-out timestamp

A work period may initially have no check-out timestamp.

An open work period represents an employee currently working.

Overnight work periods are allowed.

For example:

```
22:00 ───────────────── 06:00 next day
```

must be supported.

Do not prohibit a work period from crossing midnight.

---

# 8. Timestamp Handling

The authoritative timestamp must come from the server.

The database must store an unambiguous absolute timestamp, preferably as an instant/UTC-compatible representation.

The user's browser timezone is used when presenting dates and times to the user.

Conceptually:

```
Database:
2026-09-23T08:03:14Z

User interface:
23.09.2026 10:03
```

The application must never rely on the browser clock as the authoritative time source.

The browser timezone should be detected and used for presentation and calendar/date calculations where appropriate.

---

# 9. Check-In

The main employee page contains a prominent CHECK-IN button.

The button must be large enough to be comfortably usable by older employees.

When the employee clicks CHECK-IN:

## Normal case

Create a new open work period using the current server timestamp.

Example:

```
08:03 ───────── ?
```

## Already checked in

If the employee already has an open work period, do not silently create another open period.

Ask the employee whether they want to override the existing check-in.

Example:

```
You are already checked in since 08:03.

Do you want to replace this check-in time?

[Cancel] [Replace Check-In]
```

If confirmed, replace the existing check-in timestamp with the newly requested timestamp.

Do not silently modify the existing record.

---

# 10. Check-Out

The main employee page contains a prominent CHECK-OUT button.

If the employee has an open work period:

```
08:03 ───────── ?
```

clicking CHECK-OUT creates the check-out timestamp using the server time.

If the employee does not currently have an open work period, the application must not simply reject the operation.

Instead, ask the employee for the missing check-in time.

Example:

```
No open check-in was found.

When did you start working?

[Date] [Time]

[Cancel] [Confirm]
```

After confirmation, create the corresponding work period.

The employee must be able to enter the required historical check-in time.

---

# 11. Work Period Editing

Employees may edit time records as long as the relevant timesheet has not been submitted.

Employees may:

* change check-in time;
* change check-out time;
* add a missing work period;
* delete a work period.

Employees do not need to provide a reason for these corrections.

Employees may correct historical working times.

The system must validate that changes are logically valid.

Examples of invalid data should include:

* check-out before check-in;
* impossible overlapping periods where overlaps are not permitted;
* malformed timestamps.

Do not impose an artificial maximum daily working-time restriction merely because labor law may contain such restrictions.

The application should calculate and display actual worked time.

Potential legal violations must not automatically block recording time unless a separately defined business rule explicitly requires such blocking.

---

# 12. Working Hours

The application must calculate:

* total worked time per day;
* total break time per day.

There is deliberately no fixed working schedule.

Do not calculate:

* overtime;
* undertime;
* contractual target hours;
* daily target hours;
* weekly target hours.

The application is a time-recording system, not a working-time balance system.

Local labor-law restrictions may exist, but the application must not infer or enforce them unless explicitly configured as business rules.

The application should make actual recorded working time clearly visible to the employee.

---

# 13. Break Calculation

For a day with multiple work periods:

```
08:00 ─── 12:00
13:00 ─── 17:00
```

calculate:

```
Worked: 8h
Break: 1h
```

Break time is derived from gaps between work periods.

Do not require employees to explicitly record breaks as separate entities unless this is required by a future business requirement.

---

# 14. Main Employee Page

After authentication, the employee should arrive at the time-recording page.

The UI should be simple and optimized for accessibility and older users.

The page should contain:

1. Large CHECK-IN button
2. Large CHECK-OUT button
3. Current working status
4. Today's date
5. Today's timeline
6. Total worked time today
7. Total break time today

Example conceptual layout:

```
+------------------------------------------------+
|              Employee Time Tracking            |
|                                                |
|   +----------------+   +----------------+      |
|   |                |   |                |      |
|   |   CHECK-IN     |   |   CHECK-OUT    |      |
|   |                |   |                |      |
|   +----------------+   +----------------+      |
|                                                |
|   Currently working since 08:03                |
|                                                |
|   Wednesday, 23 September 2026                 |
|                                                |
|   00  02  04  06  08  10  12  14 ... 24       |
|                      ●────────●                |
|                     08:03    12:07             |
|                              ●────────────●     |
|                             12:48         ...   |
|                                                |
|   Worked: 8h 16m                               |
|   Break: 41m                                   |
+------------------------------------------------+
```

The timeline must visually connect check-in and check-out events with lines.

Open periods must be visually distinguishable.

The timeline must update immediately after time-recording operations.

The employee should see the currently accumulated working time while checked in.

---

# 15. Employee Navigation

Use Vaadin's standard navigation/components/theme.

The primary employee navigation should include:

* Today
* My Timesheet

Managers additionally see:

* Approvals

Administrators additionally see:

* Employees
* Public Holidays
* Audit Log

Do not create a Departments administration page.

---

# 16. Monthly Timesheet

The employee's report/timesheet page displays the current calendar month.

The original concept of "past 30 days" is superseded by this requirement:

**The reporting period is the calendar month.**

Examples:

```
01 September – 30 September
01 October – 31 October
```

The user must be able to navigate to previous and subsequent calendar months where appropriate.

The timesheet page should show:

* calendar/date;
* hours 00–24;
* one timeline row per day;
* check-in/check-out periods;
* visually connected working periods;
* daily worked hours;
* daily break time;
* monthly total worked time;
* timesheet status.

Weekends must be visually distinguishable.

Public holidays must be visually distinguishable.

---

# 17. Monthly Submission

An employee may submit a timesheet only after the calendar month has ended.

Example:

The September timesheet cannot be submitted on September 23.

It can only be submitted after September 30 has ended.

Submission must be prohibited before the period is complete.

The employee must be able to review and correct all records before submission.

---

# 18. Timesheet State Machine

Use the following business state machine:

```
DRAFT
  |
  | Submit
  v
SUBMITTED
  |
  +------------------+
  |                  |
  | Approve          | Reject
  v                  v
APPROVED           REJECTED
                      |
                      | Employee corrects
                      v
                    DRAFT
                      |
                      | Submit
                      v
                   SUBMITTED
```

Rules:

## DRAFT

* Employee may edit time records.
* Employee may submit once the calendar month is complete.

## SUBMITTED

* Employee cannot modify the timesheet.
* Manager can approve or reject.
* System records submission in audit history.

## REJECTED

* Employee can edit all records in the rejected period.
* Employee can correct the timesheet.
* Employee can resubmit.
* Manager rejection reason must be visible to the employee.

## APPROVED

* Timesheet becomes immutable.
* Employee cannot modify it.
* Manager cannot modify it.
* Audit history remains available.
* The timesheet cannot be resubmitted or altered.

---

# 19. Approval Process

The employee initiates approval using a prominent:

```
Submit for Approval
```

button.

The application must clearly communicate the consequences of submission.

After submission:

```
Employee
   |
   | Submit
   v
SUBMITTED
   |
   v
Manager Approval
```

The manager can:

* Approve
* Reject

The manager must provide a rejection reason when rejecting.

Approval and rejection actions must be audited.

---

# 20. Manager Approval Page

Managers have a dedicated Approvals page.

The page contains a table of pending approvals.

Columns:

* Employee
* Period
* Status
* Action

Example:

```
Employee       Period             Status       Action
---------------------------------------------------------
John Smith     September 2026     Submitted    Review
Jane Doe       September 2026     Submitted    Review
```

Approvals must be handled one employee/timesheet at a time.

Do not provide bulk approval.

The manager can open a timesheet and see the same timeline visualization used by the employee.

The manager must be able to review all relevant working periods before approving or rejecting.

The manager must be able to switch between:

```
My Direct Employees
```

and:

```
My Department
```

The manager must be able to filter the approval list.

Potential filters include:

* employee;
* date/month;
* status.

Do not implement filters that are not useful or technically justified.

---

# 21. Historical Manager View

Managers must be able to view already approved historical timesheets.

Historical approved records are read-only.

The manager must not be able to modify approved records.

---

# 22. Administrator

Administrators can:

* view all employees;
* manage employee information;
* manage employee roles;
* manage employee managers;
* manage employee departments;
* configure public holidays;
* view all timesheets;
* view audit logs.

Administrators cannot directly modify employee time records.

Administrators cannot approve employee timesheets.

Approval remains a manager responsibility.

---

# 23. Public Holidays

Administrators can configure public holidays.

Keep this intentionally simple.

A public holiday contains at minimum:

* date;
* name.

Public holidays are primarily used for display.

The application does not calculate expected working hours based on public holidays.

The UI should visually distinguish holidays from normal working days.

The implementation should leave room for future country/region-specific holiday configuration without unnecessarily complicating the first version.

---

# 24. Audit Trail

The application must maintain a comprehensive audit trail.

Audit at least the following events:

* check-in;
* check-out;
* manual creation of a work period;
* modification of a work period;
* deletion of a work period;
* timesheet submission;
* timesheet rejection;
* timesheet approval;
* employee changes;
* role changes;
* manager changes;
* department changes;
* public-holiday changes;
* other security-sensitive administrative actions.

An audit record should contain, where applicable:

* timestamp;
* actor/user;
* action;
* affected entity;
* entity ID;
* previous value;
* new value;
* relevant context.

Audit records are append-only.

Audit records must not be editable or deletable through the application.

The audit mechanism must remain useful across multiple application pods.

---

# 25. Data Integrity

Employee time data must be protected from unauthorized modification.

Employees:

* may modify their own DRAFT timesheets;
* cannot modify SUBMITTED timesheets;
* cannot modify APPROVED timesheets;
* cannot modify another employee's records.

Managers:

* may review their authorized employees;
* may approve/reject SUBMITTED timesheets;
* cannot modify approved time records;
* cannot directly modify employee time records as an administrative operation.

Administrators:

* may view all data;
* may manage employee/master data;
* may not directly modify time records;
* may not approve timesheets.

All authorization checks must occur server-side.

---

# 26. Concurrency and Optimistic Locking

The application must support multiple pods and multiple simultaneous sessions.

Use appropriate optimistic locking/versioning for mutable domain entities where concurrent updates are possible.

For example, if two browser sessions attempt to modify the same DRAFT timesheet:

* detect stale data;
* prevent silent overwriting;
* present an understandable error to the user.

Do not rely on Vaadin session state as a concurrency mechanism.

---

# 27. Accessibility

Accessibility is an explicit product requirement.

The application should support:

* large buttons;
* large readable text;
* high contrast;
* keyboard navigation;
* accessible labels;
* appropriate focus handling;
* screen-reader-friendly controls;
* clear validation/error messages;
* WCAG-aligned implementation where practical.

The check-in and check-out controls are especially important because the application is intended to be usable by older employees.

Do not rely solely on color to communicate state.

---

# 28. Responsive Design

The application must work on:

* desktop;
* tablet;
* mobile.

On desktop/tablet, the CHECK-IN and CHECK-OUT buttons may appear side-by-side.

On narrow mobile screens, they must stack vertically.

The timeline must remain usable on narrow screens.

Avoid requiring horizontal scrolling for normal primary actions where reasonably possible.

---

# 29. Internationalization

The application supports:

* German
* English

The UI must be internationalized rather than containing hard-coded user-facing strings.

Dates and times should follow the user's locale.

The architecture should allow additional languages to be added later.

---

# 30. Error Handling

User-facing errors must be understandable to non-technical users.

Never expose raw technical exceptions such as:

```
ConstraintViolationException
NullPointerException
SQL exceptions
```

to end users.

Technical details must be logged server-side.

Example:

Instead of:

```
ConstraintViolationException
```

display:

```
"The time entry could not be saved. Please try again."
```

Validation messages should explain what the user needs to correct.

---

# 31. Legal and Compliance Considerations

The application is intended for use in Germany and must take German labor/time-recording requirements into account.

However:

**Do not invent or hard-code legal interpretations.**

The implementation specification must:

* identify legally relevant requirements;
* keep potentially changing legal rules configurable where appropriate;
* document assumptions;
* clearly distinguish technical requirements from legal/business interpretations;
* identify requirements that require validation by the responsible legal/business stakeholders.

In particular, the application must not automatically block an employee from recording more than a particular number of hours merely because a legal working-time limit may exist.

The system records actual working time.

Legal compliance rules that may change over time must not be hidden inside arbitrary application logic.

---

# 32. Data Retention

Employee time-record data should be retained for up to one year according to the current application requirement.

The implementation should make the retention period configurable.

Do not implement destructive deletion without:

* explicit business requirements;
* audit implications;
* legal/compliance review.

Personal data should be minimized in accordance with GDPR principles.

Only data necessary for the application should be stored.

---

# 33. No External Business Integrations for Version 1

Version 1 does not require:

* HR-system integration;
* payroll integration;
* REST API for external consumers;
* CSV export;
* Excel export;
* PDF export;
* Teams integration;
* external reporting integration.

The architecture should not unnecessarily prevent future integrations.

---

# 34. Performance and Scale

The application should support up to approximately:

**10,000 employees/users.**

There are no specific formal SLA requirements at this stage.

Nevertheless:

* database queries must be appropriately indexed;
* N+1 query problems should be avoided;
* monthly timesheet queries should be efficient;
* authorization queries should be efficient;
* audit-log access should be paginated;
* manager approval lists should be paginated where appropriate;
* the application must remain suitable for horizontal scaling.

Do not prematurely introduce distributed infrastructure that is not necessary.

---

# 35. Domain Model

Before implementation, explicitly design and document the domain model.

At minimum, evaluate the following entities:

## Employee

Potential fields:

* id
* name
* email
* department
* managerId
* roles
* active
* version
* createdAt
* updatedAt

## Department

Potential fields:

* id
* name
* active
* createdAt
* updatedAt

## WorkPeriod

Potential fields:

* id
* employeeId
* checkIn
* checkOut
* createdAt
* createdBy
* updatedAt
* updatedBy
* version

## Timesheet

Potential fields:

* id
* employeeId
* periodStart
* periodEnd
* status
* submittedAt
* submittedBy
* rejectedAt
* rejectedBy
* rejectionReason
* approvedAt
* approvedBy
* version

## PublicHoliday

Potential fields:

* id
* date
* name
* createdAt
* updatedAt

## AuditEvent

Potential fields:

* id
* timestamp
* actor
* action
* entityType
* entityId
* previousValue
* newValue
* context

These are starting points, not mandatory implementation schemas.

The development agent must evaluate and refine them.

Do not create redundant entities merely for architectural fashion.

---

# 36. Domain Invariants

Explicitly identify and implement domain invariants.

Examples:

* A work period cannot have check-out before check-in.
* An approved timesheet cannot be modified.
* A submitted timesheet cannot be modified by the employee.
* Only authorized managers can approve a submitted timesheet.
* Only the employee owning a DRAFT timesheet can edit it.
* A timesheet cannot be submitted before the calendar month is complete.
* A rejected timesheet can return to DRAFT.
* A DRAFT timesheet can become SUBMITTED.
* A SUBMITTED timesheet can become APPROVED or REJECTED.
* An APPROVED timesheet is terminal/immutable.
* Audit records cannot be modified through the application.

Identify additional invariants before implementation.

---

# 37. State Transition Specification

Create an explicit state-transition model.

At minimum:

```
DRAFT
  |
  | submit
  v
SUBMITTED
  |
  +----------------+
  |                |
  | approve        | reject(reason)
  v                v
APPROVED        REJECTED
                    |
                    | edit
                    v
                  DRAFT
```

For every transition define:

* allowed actor;
* preconditions;
* database changes;
* audit events;
* notifications;
* resulting state;
* invalid transition behavior.

---

# 38. Notification Requirements

Email notifications are required.

Employees should receive email notifications when:

* a timesheet is approved;
* a timesheet is rejected.

Managers should receive an email notification when:

* an employee submits a timesheet for approval.

The implementation should abstract email delivery behind an appropriate service interface.

Do not tightly couple domain logic to a particular email provider.

Email templates must be internationalized.

The exact email provider is not yet specified.

---

# 39. Database

Development:

```
H2
```

Production:

```
PostgreSQL
```

The database schema must be managed through versioned database migrations.

Do not rely on automatically generated production schema updates.

Use an appropriate migration technology such as Flyway or Liquibase.

Database constraints should enforce important invariants where practical, while domain validation remains in the application layer.

Use indexes for common access paths such as:

* employee ID;
* manager ID;
* department ID;
* timesheet employee + period;
* timesheet status;
* work-period employee + timestamp;
* audit timestamp/entity.

---

# 40. Testing

The application must be thoroughly tested.

Include:

## Unit tests

Test:

* time calculations;
* break calculations;
* state transitions;
* authorization rules;
* date/period logic;
* validation;
* overnight periods;
* month completion rules.

## Integration tests

Test:

* persistence;
* database constraints;
* transactions;
* authentication integration boundaries;
* approval workflow;
* optimistic locking;
* audit logging.

## UI tests

Test critical user journeys such as:

1. Login
2. Check in
3. Check out
4. Create multiple work periods
5. Correct a historical entry
6. Submit monthly timesheet
7. Manager reviews timesheet
8. Manager approves
9. Manager rejects
10. Employee corrects rejected timesheet
11. Employee resubmits
12. Approved timesheet becomes immutable

## Security tests

Test that:

* employees cannot access other employees' records;
* employees cannot approve timesheets;
* managers cannot access unauthorized departments;
* managers cannot modify approved records;
* unauthorized users cannot access administrative functions;
* IAM-authenticated but unregistered users are denied access.

---

# 41. Implementation Methodology

Follow this process strictly.

## Phase 1 — Requirements Analysis

Analyze this specification.

Identify:

* ambiguities;
* contradictions;
* missing requirements;
* assumptions;
* technical risks;
* security risks;
* legal/compliance questions.

Do not silently resolve material ambiguities.

If an ambiguity materially changes business behavior, stop and ask for clarification.

For minor implementation details, choose a sensible conventional solution and document the assumption.

---

## Phase 2 — Domain Specification

Produce:

* domain model;
* entity relationships;
* aggregates where appropriate;
* domain invariants;
* state machines;
* authorization rules;
* time calculation rules.

The domain model must be independent of Vaadin UI concerns.

---

## Phase 3 — Architecture Specification

Define:

* application architecture;
* package/module structure;
* persistence architecture;
* security architecture;
* IAM abstraction;
* notification abstraction;
* audit architecture;
* database migration strategy;
* Kubernetes/container strategy;
* configuration strategy;
* observability strategy.

Prefer a clear modular monolith architecture unless there is a concrete reason to introduce microservices.

Do not introduce microservices merely because Kubernetes is being used.

---

# 42. Recommended Architectural Direction

Unless analysis identifies a strong reason otherwise, use a modular Spring Boot/Vaadin application.

Conceptually:

```
+------------------------------------------------------+
|                    Vaadin UI                         |
+------------------------------------------------------+
                     |
+------------------------------------------------------+
|              Application Services                   |
+------------------------------------------------------+
                     |
+------------------------------------------------------+
|                 Domain Layer                        |
| Time Recording | Timesheets | Approval | Employees |
+------------------------------------------------------+
                     |
+------------------------------------------------------+
|             Infrastructure Layer                    |
| PostgreSQL | IAM | Email | Audit | Configuration    |
+------------------------------------------------------+
```

Keep business rules out of Vaadin views.

Vaadin views should invoke application/domain services.

---

# 43. UI Architecture

Use Vaadin standard components and theme.

Do not introduce a custom frontend framework unless technically necessary.

Favor:

* clear typography;
* large controls;
* generous spacing;
* accessible labels;
* responsive layouts;
* reusable timeline components;
* reusable timesheet components.

The timeline visualization should be implemented as a reusable component that can be used by:

* employee Today page;
* employee Timesheet page;
* manager Review page.

---

# 44. Timeline Component

Design a reusable timeline component.

It should display:

* 00:00–24:00 scale where appropriate;
* working periods;
* check-in;
* check-out;
* open work periods;
* multiple periods;
* overnight periods;
* daily total;
* break time.

The component must be reusable for both employee and manager views.

Do not duplicate timeline logic between views.

---

# 45. Security Architecture

Use Spring Security with OIDC-compatible authentication.

Separate:

1. Authentication
2. Application identity resolution
3. Authorization

Authentication answers:

> Who authenticated?

Employee lookup answers:

> Which application employee does this identity represent?

Authorization answers:

> What may this employee do?

Do not use email-address checks scattered throughout the UI.

Centralize authorization decisions in appropriate services/policies.

---

# 46. Configuration

Configuration should support environment-specific settings without recompilation.

Examples:

* database URL;
* database credentials;
* IAM provider;
* OIDC client configuration;
* email configuration;
* retention period;
* application timezone/display defaults where appropriate;
* feature flags if required.

Secrets must never be committed to source control.

Use environment variables/secrets appropriate for Docker/Kubernetes.

---

# 47. Containerization

Provide a production-ready Docker image.

The application must:

* start without manual intervention;
* expose appropriate health endpoints;
* support graceful shutdown;
* externalize configuration;
* avoid persistent local filesystem state;
* be safe to run in multiple pods.

Provide Kubernetes-compatible configuration/documentation.

The application must be horizontally scalable.

---

# 48. Observability

Provide appropriate:

* structured logging;
* error logging;
* health checks;
* readiness checks;
* liveness checks.

Do not log sensitive personal data unnecessarily.

Authentication credentials, tokens, passwords, and sensitive information must never appear in application logs.

---

# 49. Documentation

Produce developer documentation covering:

* architecture;
* domain model;
* state transitions;
* authorization model;
* database schema;
* local development setup;
* H2 setup;
* PostgreSQL setup;
* IAM configuration;
* email configuration;
* Docker usage;
* Kubernetes deployment;
* testing;
* configuration;
* known assumptions;
* legal/business rules requiring validation.

Also provide an end-user-oriented description of the major workflows where appropriate.

---

# 50. Definition of Done

A feature is not considered complete merely because the code compiles.

A feature is complete when:

* the requirement is represented in the specification;
* the domain behavior is defined;
* authorization is defined;
* validation is implemented;
* persistence is implemented;
* audit behavior is implemented where applicable;
* UI behavior is implemented;
* error handling is implemented;
* relevant tests exist;
* internationalization is implemented;
* accessibility has been considered;
* documentation is updated;
* the implementation has been verified against acceptance criteria.

---

# 51. Acceptance Criteria

Create explicit acceptance criteria for every major user journey.

At minimum, include:

## Employee

### Login

Given an authenticated IAM user whose email exists as an active employee,

when they access the application,

then they can use the application according to their application roles.

### Check-in

Given an employee is not currently working,

when they click Check-In,

then a new open work period is created using the server timestamp.

### Check-out

Given an employee has an open work period,

when they click Check-Out,

then the open work period receives the current server timestamp.

### Missing check-in

Given an employee has no open work period,

when they click Check-Out,

then the application asks for the missing check-in time and creates the period after confirmation.

### Multiple periods

Given an employee works multiple periods,

when they enter them,

then all periods appear correctly on the timeline and the daily total is calculated correctly.

### Historical correction

Given a timesheet is DRAFT,

when the employee edits a work period,

then the change is persisted and audited.

### Submission

Given the calendar month has ended,

when the employee submits the timesheet,

then it changes from DRAFT to SUBMITTED and becomes read-only for the employee.

### Early submission

Given the calendar month has not ended,

when the employee attempts to submit,

then submission is prevented.

### Rejection

Given a manager rejects a timesheet,

then the employee sees REJECTED and the rejection reason.

### Resubmission

Given a timesheet is REJECTED,

when the employee corrects it and resubmits,

then the state returns to SUBMITTED.

### Approval

Given a manager approves a submitted timesheet,

then the state becomes APPROVED and the timesheet becomes immutable.

---

# 52. Development Behavior for the AI Agent

During implementation, follow these rules:

1. Do not silently invent important business requirements.
2. Do not remove requirements because they appear inconvenient.
3. Do not introduce unnecessary complexity.
4. Prefer simple enterprise patterns over architectural novelty.
5. Keep business logic independent of Vaadin UI.
6. Keep authentication independent of a specific IAM provider.
7. Keep email delivery abstracted.
8. Keep audit logging centralized.
9. Use server-side authorization.
10. Treat approved timesheets as immutable.
11. Treat audit events as immutable.
12. Use database persistence rather than application memory for business state.
13. Ensure multi-pod operation.
14. Write tests alongside implementation.
15. When a requirement cannot be implemented as written, explain why before changing it.
16. When making an assumption, document it explicitly.
17. Never hide a requirement change inside implementation code.

---

# 53. Required Development Deliverables

Before implementation, produce the following artifacts:

1. Requirements specification
2. Assumptions and open questions
3. Domain model
4. Entity relationship model
5. State-transition diagrams
6. Authorization matrix
7. Application architecture
8. Database schema design
9. UI/navigation specification
10. Timeline component specification
11. IAM architecture
12. Notification architecture
13. Audit architecture
14. Testing strategy
15. Kubernetes/container strategy
16. Implementation roadmap
17. Acceptance criteria

Then implement the application incrementally.

After every implementation phase:

* run tests;
* verify the implementation against the specification;
* identify deviations;
* update documentation;
* report remaining risks.

---

# 54. Final Principle

The specification is the source of truth for intended behavior.

Code is an implementation of the specification.

When code and specification diverge, do not silently choose one.

Identify the divergence, explain its impact, and request or document the required decision.

The objective is not merely to generate code.

The objective is to create a maintainable, testable, secure enterprise time-recording application whose implementation can be traced back to explicit requirements and acceptance criteria.