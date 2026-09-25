# Domain model

The precise field lists are in [`spec/datamodel/datamodel.md`](../spec/datamodel/datamodel.md); this page explains how
the pieces fit together. All timestamps are absolute UTC instants taken from the **server clock** (an injectable
`java.time.Clock`); the browser only decides how they are displayed.

## Entities

```
Department 1 ── n Employee ──(manager)── Employee
                    │
                    ├── n TimeEntry     one work period: check-in .. check-out
                    └── n Timesheet     one per employee and month, with a status

PublicHoliday                            a date and a name, informational only
AuditLogEntry                            append-only history of changes and logins
```

| Entity | Purpose | Notes |
|--------|---------|-------|
| `Employee` | Who can sign in and with which role (`EMPLOYEE`, `MANAGER`, `ADMIN`) | Unique lower-case email; never deleted, only deactivated; `version` for optimistic locking |
| `Department` | Groups employees; a manager also covers their own department | |
| `TimeEntry` | A work period. Open while `checkOutAt` is empty | Unique `open_employee_id` allows only one open period per employee, even across pods; a check-out never precedes the check-in; `version` |
| `Timesheet` | The month of an employee with its approval state | Unique per employee and month; created the first time the month is opened; a month without a record counts as a draft; `version` |
| `PublicHoliday` | Marks a date in the month view | Never changes worked time; unique date; `version` |
| `AuditLogEntry` | Who did what, when, with old and new values (JSON) and a reason | Written in the same transaction as the change; the repository has no update or delete |

Breaks are **derived**: the time between two work periods of a day is break time. Nobody records a break.

## Timesheet state machine

```
  DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED (final)
                       │  ▲
                reject │  │ submit again
                       ▼  │
                     REJECTED  (the employee corrects the days)
```

A month can only be submitted once it is over (in the employee's time zone), has at least one work period and no
period that is still open.

| From | Action | To | Who | Also |
|------|--------|----|-----|------|
| DRAFT | submit | SUBMITTED | the employee | audit `SUBMIT`, email to the direct manager |
| SUBMITTED | approve | APPROVED | a manager of that employee | audit `APPROVE`, email to the employee |
| SUBMITTED | reject | REJECTED | a manager of that employee | needs a reason; audit `REJECT`, email to the employee |
| REJECTED | submit | SUBMITTED | the employee | audit `SUBMIT` ("Resubmission after rejection"), email to the direct manager |

Everything else is refused (`TimesheetStatus`, `Timesheet.submit/approve/reject`). Time entries can be corrected or
deleted only while the timesheet is `DRAFT` or `REJECTED` (`TimesheetStatus.allowsCorrections`); a submitted or
approved month is locked. A concurrent second decision or submission fails through the `@Version` of the timesheet.

## Invariants

* One open work period per employee (database constraint).
* A check-out is never before its check-in (database constraint; the service also clamps a clock adjustment).
* One timesheet per employee and month; one public holiday per date; one employee per email (all unique constraints).
* A manager is never above themselves in the chain of managers; the last active administrator cannot be deactivated
  or demoted.
* An administrator never approves or rejects; nobody decides on their own timesheet.
* Every significant change has an audit entry written in the same transaction.

## Database schema

Flyway migrations in `src/main/resources/db/migration` (portable SQL, identical on H2 and PostgreSQL):

| Migration | Content |
|-----------|---------|
| `V1__employee_department_audit_log.sql` | `department`, `employee`, `audit_log` (with indexes) |
| `V2__time_entry.sql` | `time_entry` with the open-period and check-out constraints |
| `V3__timesheet.sql` | `timesheet` with the unique employee/month constraint |
| `V4__timesheet_approval_and_public_holiday.sql` | approval columns, `public_holiday` |
| `V5__optimistic_locking.sql` | `version` on `employee` and `public_holiday` |
| `V6__audit_log_user_indexes.sql` | indexes for the audit log's user filter and the last login |
| `db/vendor/postgresql/V7__audit_log_append_only.sql` | PostgreSQL only: triggers that refuse `UPDATE`, `DELETE` and `TRUNCATE` on `audit_log` |

Query notes for large installations (about 10,000 employees): the employee list is searched, sorted and paged in the
database (25 per page), the audit log is paged (25 per page), and the last login is read with an index on
`audit_log (action, user_id, occurred_at)`. The free-text search of the employee list uses `like '%text%'` on lower-cased
name, email and department; if that becomes slow on a very large table, add a `pg_trgm` index in a new migration.
