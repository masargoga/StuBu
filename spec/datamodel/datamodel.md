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
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| updatedAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### Department
Organizational unit for grouping employees.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| name | String | Unique, Not null | Department name |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |

### TimeEntry
Individual work period (check-in to check-out).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| employeeId | Long (FK) | Not null | References Employee |
| date | LocalDate | Not null | Date of the work period |
| checkInTime | LocalTime | Not null | Check-in time |
| checkOutTime | LocalTime | Nullable | Check-out time (null while active) |
| isActive | Boolean | Not null, default false | True if currently clocked in |
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
| submittedAt | LocalDateTime | Nullable | When submitted for approval |
| approvedAt | LocalDateTime | Nullable | When approved |
| rejectionReason | String | Nullable | Reason if rejected |
| rejectedAt | LocalDateTime | Nullable | When rejected |
| createdAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| updatedAt | Instant (UTC) | Not null | Audit timestamp, set by the server |
| Unique constraint | (employeeId, year, month) | | One timesheet per employee per month |

### PublicHoliday
Calendar configuration for holidays.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | Long (PK) | Auto-generated | |
| date | LocalDate | Not null, Unique | Holiday date |
| name | String | Not null | Holiday name |
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
- **BR-05:** A Timesheet can only be created/modified when in DRAFT status.
- **BR-06:** A Timesheet must contain at least one TimeEntry to be submitted.
- **BR-07:** Once submitted (SUBMITTED status), a Timesheet cannot be edited by the employee.
- **BR-08:** Only the manager (or an admin) can approve or reject a submitted Timesheet.
- **BR-09:** A rejected Timesheet returns to DRAFT status, allowing the employee to correct it.
- **BR-10:** All significant data changes must be logged to AuditLog with user context.
- **BR-11:** Public holidays are informational only; they do not automatically exclude days from timesheet calculations.
