# Enterprise Employee Time Tracking Application - Specifications Summary

This document provides an overview of the complete specification set for the time tracking application.

## Specification Files

### Core Context
- **`project-context.md`** — Vision, users, and constraints
- **`architecture.md`** — Technology stack and application structure
- **`datamodel/datamodel.md`** — Entity definitions, relationships, and business rules

### Use Cases (14 use cases)

#### Authentication & Access
- **UC-001** `use-case-001-authenticate-with-iam.md` — OIDC/OAuth 2.0 login with IAM abstraction (Entra ID, Google)

#### Employee Features
- **UC-002** `use-case-002-record-check-in-check-out.md` — Record work periods with check-in/check-out
- **UC-003** `use-case-003-view-daily-timesheet.md` — View today's timeline and total hours
- **UC-004** `use-case-004-correct-historical-time-entries.md` — Edit or delete historical time entries
- **UC-005** `use-case-005-view-monthly-timesheet.md` — View monthly timesheet before submission
- **UC-006** `use-case-006-submit-timesheet.md` — Submit completed monthly timesheet for approval
- **UC-008** `use-case-008-resubmit-rejected-timesheet.md` — Correct and resubmit rejected timesheet

#### Manager Features
- **UC-007** `use-case-007-review-approve-reject-timesheet.md` — Review and approve/reject employee timesheets
- **UC-009** `use-case-009-manager-view-employee-timesheet.md` — View complete timesheet and timeline
- **UC-010** `use-case-010-manager-switch-employee-scope.md` — Toggle between direct reports and department

#### Administrator Features
- **UC-011** `use-case-011-admin-manage-employees.md` — Create, edit, deactivate employees
- **UC-012** `use-case-012-admin-configure-public-holidays.md` — Add, edit, delete public holidays
- **UC-013** `use-case-013-admin-view-audit-logs.md` — View and search immutable audit trail
- **UC-014** `use-case-014-admin-view-all-employee-data.md` — View all employees and timesheets

## Key Architectural Principles

### Scalability
- Horizontal scaling for Kubernetes deployment
- All persistent state in PostgreSQL (no in-memory state as source of truth)
- Session-independent application logic
- Optimistic locking for concurrent updates

### Authentication & Authorization
- OIDC/OAuth 2.0 abstraction (not tightly coupled to specific provider)
- Server-side authorization enforcement (not UI visibility)
- Three roles: EMPLOYEE, MANAGER, ADMIN
- Email matching between IAM and application database

### Data Integrity
- Immutable audit trail for all significant changes
- State machine enforcement for timesheet workflow (DRAFT → SUBMITTED → APPROVED/REJECTED)
- Approved timesheets are immutable
- Database constraints enforce key invariants

### Time Recording Domain
- Multiple work periods per day supported
- Overnight work periods supported
- Check-in uses current server time (authoritative)
- Historical time entry corrections allowed before submission
- Break time calculated from gaps between periods

### Timesheet Workflow
```
DRAFT (employee can edit) 
  ↓ Submit (month must be complete)
SUBMITTED (read-only for employee)
  ├→ Approve → APPROVED (immutable)
  └→ Reject → REJECTED (returns to DRAFT for employee to edit and resubmit)
```

### Audit Trail
- Immutable records of all significant operations
- Captures: timestamp, actor, action, entity, old/new values, reason
- Supports compliance and troubleshooting
- Accessible only to administrators

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Runtime | Java 25 |
| Framework | Spring Boot |
| UI | Vaadin (server-side) |
| Database (Dev) | H2 |
| Database (Prod) | PostgreSQL |
| Security | Spring Security + OIDC/OAuth 2.0 |
| Persistence | JPA/Hibernate |
| Container | Docker |
| Orchestration | Kubernetes |
| Testing | JUnit 5, Vaadin Browserless Tests, Vitest |

## Domain Model Summary

### Core Entities
- **Employee** — User with email, role, manager, department, active status
- **Department** — Organizational unit grouping employees
- **TimeEntry** — Individual work period (check-in to check-out)
- **Timesheet** — Monthly container with approval workflow state
- **PublicHoliday** — Calendar configuration (informational only)
- **AuditLog** — Immutable audit trail

### Key Relationships
```
Department
  ↓ (many-to-one)
Employee
  ├→ Manager (self-referencing, optional)
  ├→ TimeEntry (many-to-one)
  └→ Timesheet (one per month, many-to-one)

AuditLog (references Employee, TimeEntry, Timesheet)
PublicHoliday (standalone, informational)
```

## Business Rules Highlights

### Authentication & Access
- Only active employees can log in
- Email must match between IAM and application database
- Inactive employees are denied access with clear message

### Time Recording
- Only one active work period per employee at a time
- Check-out time must be after check-in time
- No time entries for future dates (except public holidays can be future-dated)
- Multiple periods on same day allowed and encouraged

### Timesheet Submission
- Cannot submit until calendar month is complete
- Submission is mandatory for monthly record closure
- Can only be submitted when status is DRAFT

### Manager Authorization
- Can view direct reports only (or all department employees if in scope)
- Can approve/reject SUBMITTED timesheets
- Cannot edit or delete time records
- Cannot approve their own timesheets (if needed, escalate to admin/sr.manager)

### Admin Access
- Full visibility to all data
- Cannot directly modify time records (employees/managers own that)
- Cannot approve timesheets (manager responsibility)
- Can manage master data (employees, holidays)

## UI Navigation Structure

### Employee Views
- **Login** — IAM authentication
- **Today** — Current day timeline with check-in/check-out buttons
- **My Timesheet** — Monthly view with submission workflow
- **Settings/Profile** (optional future feature)

### Manager Views
- **Approvals** — Pending timesheet approval queue
- **Employee Review** — Detailed timesheet and timeline for review
- **Scope Switcher** — Toggle between direct reports / department employees

### Administrator Views
- **Employee Management** — Create, edit, deactivate employees
- **Public Holidays** — Configure holidays for calendar
- **Audit Logs** — Search and view immutable audit trail
- **Directory** — View all employees and timesheets

## Accessibility & Usability Requirements

- Large, accessible buttons (especially check-in/check-out)
- High contrast, readable text
- Keyboard navigation support
- Screen reader friendly
- Responsive design (desktop, tablet, mobile)
- Clear error messages
- WCAG alignment where practical
- Designed for older employees and non-technical users

## Internationalization

- **Languages:** English, German, Spanish, French (chosen by the user, UC-016)
- **Date/Time:** Follow user locale
- **Labels/Messages:** All UI strings externalized

## Security Considerations

### Authentication
- Server-side validation of tokens
- Session-based authentication
- Secure token storage
- Failed login attempt logging

### Authorization
- Server-side enforcement (not UI-based)
- Role-based access control
- Manager scope validation
- Audit logging of privileged operations

### Data Protection
- No personal data exposure in error messages
- Secrets externalized (no hardcoding)
- Audit trail prevents tampering
- Optimistic locking prevents data corruption

## Testing Strategy

### Unit Tests
- Time calculations
- Break calculations
- State transitions
- Authorization rules
- Validation rules
- Date/period logic
- Overnight periods

### Integration Tests
- Persistence and transactions
- Database constraints
- Authentication integration
- Approval workflow
- Audit logging
- Optimistic locking

### UI Tests
- Critical user journeys (14 major flows from use cases)
- Accessibility compliance
- Responsive behavior

### Security Tests
- Cross-employee data access prevention
- Role-based function restriction
- Unauthorized access denial
- Unregistered user rejection

## Deployment & Operations

### Containerization
- Docker image with externalized configuration
- Health check endpoints
- Graceful shutdown
- No persistent local filesystem state
- Multi-pod safe

### Kubernetes Compatibility
- Horizontally scalable
- Stateless application pods
- Shared PostgreSQL backend
- Environment variable configuration
- Kubernetes-compatible health probes

### Observability
- Structured logging
- Error logging with context
- Health/readiness/liveness checks
- No sensitive data in logs
- Audit trail for compliance

## Development Phases (all implemented)

All 14 use cases are implemented and tested; the phases below are the order in which they were built. What was decided
and assumed while building is in [docs/assumptions-and-legal.md](../docs/assumptions-and-legal.md).

### Phase 1: Foundation
- IAM authentication & employee lookup
- Database schema & migrations
- Core domain model & services
- Employee check-in/check-out with persistence
- Daily timesheet view

### Phase 2: Monthly Workflow
- Monthly timesheet view & calculations
- Time entry editing & deletion
- Timesheet submission (state transition)
- Audit logging

### Phase 3: Manager Approval
- Manager approval view & workflow
- Rejection with reason
- Resubmission flow
- Manager scope switching

### Phase 4: Administration
- Employee management
- Public holidays configuration
- Audit log viewing
- Complete employee/timesheet directory

### Phase 5: Polish & Deployment
- UI refinement & accessibility
- Internationalization (English, German, Spanish, French)
- Performance optimization
- Security hardening
- Docker/Kubernetes deployment
- Comprehensive testing

## Known Assumptions & Decisions

1. **One timesheet per employee per month** — Calendar month is the reporting period (not "last 30 days")
2. **Soft deletion for employees** — Deactivation sets isActive=false, preserves history
3. **No automatic legal rule enforcement** — System records working time without automatically blocking hours based on labor law
4. **Break time is calculated, not recorded** — Gaps between periods = break time
5. **Public holidays are informational** — No automatic exclusion from timesheet calculations
6. **Monolithic architecture initially** — Spring Boot + Vaadin, not microservices
7. **Email immutability after creation** — Preserves audit trail integrity
8. **No bulk operations** — Approval handled one-at-a-time for traceability
9. **Timezone handling** — Database stores UTC, browser displays in user locale
10. **No external integrations V1** — Payroll, HR, email exports planned for future

## Open Questions for Stakeholders

1. **Email notifications** — Which provider? Should they be optional/configurable?
2. **Timezone strategy** — Should application support per-user timezone override, or browser-based only?
3. **Data retention policy** — Exactly how long should records be kept? Configurable or fixed?
4. **Manager approval delegation** — Can managers delegate approval to substitutes? (Not in current spec)
5. **Timesheet locking** — Can HR manually lock timesheets to prevent reopen? (Not in current spec)
6. **Break policy** — Should minimum/maximum breaks be configurable per department/company? (Not in current spec)
7. **Overnight work calculation** — Any special rules? (Currently treats as normal periods crossing midnight)
8. **Performance targets** — Specific SLA for timesheet queries or audit log searches?
9. **Data export** — Future CSV/Excel export or API required?
10. **Compliance certification** — German labor law audit required before production?

## Where to go from here

1. **Legal and business validation:** answer the open questions above and in [docs/assumptions-and-legal.md](../docs/assumptions-and-legal.md).
2. **First production rollout:** follow [docs/operations.md](../docs/operations.md) (PostgreSQL, sign-in providers, Docker, Kubernetes).
3. **Change the specification first:** new or changed behaviour starts as an update of the use case files, then `implement-use-case`.

---

**Specification Version:** 1.1  
**Status:** Implemented (14 of 14 use cases)
