# Data Model

> Entity definitions and relationships. Evolves as features are added.

## Entities

### Employee
Core user identity and organizational context.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| email | String | Unique, Not null | Used to match authenticated IAM email. Stored trimmed and lower-case; matching is case-insensitive |
| firstName | String | Not null | |
| lastName | String | Not null | |
| role | Enum | EMPLOYEE, MANAGER, ADMIN | Determines access rights. MANAGER and ADMIN also hold the EMPLOYEE role at login (they record their own time) |
| managerId | Long (FK) | Nullable | References another Employee if this employee has a manager |
| departmentId | Long (FK) | Not null | References Department |
| isActive | Boolean | Not null, default true | Soft delete for historical tracking |
| version | Long | Not null | Optimistic locking: an administrator's save based on an older version is refused |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| updatedAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### EmployeeSetting
Personal settings of an employee (UC-016). One row per employee, created when the first setting is saved; every setting is one typed column, so a further setting (for example a theme) is a further column.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| employeeId | Long (PK, FK) | References Employee | One row per employee |
| language | String(5) | Nullable; only `en`, `de`, `es`, `fr` | Application language; empty means "not chosen" (browser language, else English) |
| version | Long | Not null | Optimistic locking |
| createdAt | Instant (UTC) | Not null | Set by the server |
| updatedAt | Instant (UTC) | Not null | Set by the server |

### Department
Organizational unit for grouping employees.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| name | String | Unique, Not null | Department name |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### TimeEntry
Individual work period (check-in to check-out). Timestamps are absolute UTC instants, so periods may cross midnight; dates and times are presented in the user's browser time zone.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| employeeId | Long (FK) | Not null | References Employee |
| checkInAt | Instant (UTC) | Not null | Server timestamp of the check-in |
| checkOutAt | Instant (UTC) | Nullable | Server timestamp of the check-out; null while the period is open. Never before `checkInAt` |
| openEmployeeId | Long (FK) | Nullable, Unique | Equals `employeeId` while the period is open, otherwise null. The unique constraint guarantees at most one open period per employee, even across concurrent sessions and pods. Derived: "active" means `checkOutAt` is null |
| version | Long | Not null | Optimistic locking |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| updatedAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### Timesheet
Monthly timesheet container with approval workflow.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| employeeId | Long (FK) | Not null | References Employee |
| year | Integer | Not null | e.g., 2024 |
| month | Integer | Not null | 1-12 |
| status | Enum | DRAFT, SUBMITTED, APPROVED, REJECTED | Approval workflow state |
| submittedAt | Instant (UTC) | Nullable | When submitted for approval (set by UC-006) |
| approvedAt | Instant (UTC) | Nullable | When approved (set by UC-007) |
| approvedBy | Long (FK) | Nullable | Employee who approved |
| rejectedAt | Instant (UTC) | Nullable | When rejected (set by UC-007) |
| rejectedBy | Long (FK) | Nullable | Employee who rejected |
| rejectionReason | String | Nullable | Reason if rejected |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| updatedAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| Unique constraint | (employeeId, year, month) | | One timesheet per employee per month. Columns are named `period_year` / `period_month` (YEAR and MONTH are reserved words in H2). UC-005 creates the record (DRAFT) the first time a month is viewed; a month without a record still counts as DRAFT. The submission and approval fields exist since UC-005 but are only written by UC-006 and UC-007 |

### PublicHoliday
Calendar configuration for holidays. UC-005 only reads it (management is UC-012).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| date | LocalDate | Not null, Unique | Holiday date |
| name | String | Not null | Holiday name |
| version | Long | Not null | Optimistic locking |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### AuditLog
Immutable audit trail for compliance and troubleshooting.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| timestamp | Instant (UTC) | Not null | When the action occurred (server clock; column `occurred_at`) |
| userId | Long (FK) | Nullable | Employee who performed the action (null for system) |
| entityType | String | Not null | Type of entity affected (e.g., "TimeEntry", "Timesheet") |
| entityId | Long | Nullable | ID of the affected entity (null when none is known, e.g. a login by an unregistered email) |
| action | Enum | CREATE, UPDATE, DELETE, APPROVE, REJECT, SUBMIT, LOGIN_SUCCESS, LOGIN_FAILURE | Action performed |
| oldValues | String (JSON) | Nullable | Previous values (for auditing changes) |
| newValues | String (JSON) | Nullable | New values (for auditing changes) |
| reason | String | Nullable | Human-readable reason (e.g., rejection reason; for LOGIN_FAILURE the failure kind and attempted email) |

## Relationships

```
Department
  |
  └─ Employee (many-to-one)
       |
       ├─ Manager (self-referencing FK to Employee)
       ├─ EmployeeSetting (one-to-one, optional)
       ├─ TimeEntry (one-to-many)
       │    └─ Date groups time entries
       └─ Timesheet (one-to-many, unique per year/month)
            └─ References TimeEntries for the month

PublicHoliday
  └─ Used for calendar calculations (no direct FK)

AuditLog (one-to-many relationships)
  ├─ Employee (audit actor)
  ├─ TimeEntry (audit subject)
  └─ Timesheet (audit subject)
```

## Business Rules

- **BR-01:** Employees must have an email that matches authenticated IAM email to gain access.
- **BR-02:** Only active employees can log in and access the system.
- **BR-03:** Managers must have at least one direct report to see the manager UI.
- **BR-04:** TimeEntries for the same employee on the same date belong to the same Timesheet.
- **BR-05:** The time entries of a Timesheet can only be created, changed or deleted while it is DRAFT or REJECTED.
- **BR-06:** A Timesheet must contain at least one TimeEntry to be submitted.
- **BR-07:** Once submitted (SUBMITTED status), a Timesheet cannot be edited by the employee.
- **BR-08:** Only a manager of the employee (a direct manager, or a manager of the employee's department) can approve or reject a submitted Timesheet. Administrators can look at it but never decide.
- **BR-09:** A rejected Timesheet stays REJECTED, with its reason, until the employee has corrected it and submits it again (then it is SUBMITTED).
- **BR-10:** All significant data changes must be logged to AuditLog with user context.
- **BR-11:** Public holidays are informational only; they do not automatically exclude days from timesheet calculations.
